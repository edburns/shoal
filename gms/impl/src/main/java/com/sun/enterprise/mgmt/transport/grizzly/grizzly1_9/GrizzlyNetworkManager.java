/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.mgmt.transport.grizzly.grizzly1_9;

import com.sun.enterprise.ee.cms.core.GMSConstants;
import com.sun.enterprise.ee.cms.impl.base.GMSThreadFactory;
import com.sun.enterprise.ee.cms.impl.base.PeerID;
import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.impl.common.GMSContext;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.impl.common.GMSMonitor;
import com.sun.enterprise.mgmt.transport.*;
import com.sun.enterprise.mgmt.transport.grizzly.*;
import com.sun.grizzly.*;
import com.sun.grizzly.connectioncache.client.CacheableConnectorHandlerPool;
import com.sun.grizzly.connectioncache.spi.transport.ConnectionFinder;
import com.sun.grizzly.connectioncache.spi.transport.ContactInfo;
import com.sun.grizzly.util.GrizzlyExecutorService;
import com.sun.grizzly.util.SelectorFactory;
import com.sun.grizzly.util.ThreadPoolConfig;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sun.enterprise.mgmt.ConfigConstants.*;
import static com.sun.enterprise.mgmt.transport.grizzly.GrizzlyConfigConstants.*;

/**
 * @author Bongjae Chang
 */
public class GrizzlyNetworkManager extends com.sun.enterprise.mgmt.transport.grizzly.GrizzlyNetworkManager {

    private int maxPoolSize;
    private int corePoolSize;
    private long keepAliveTime; // ms
    private int poolQueueSize;
    private int highWaterMark;
    private int numberToReclaim;

    private String virtualUriList;
    private GrizzlyExecutorService execService;
    private ExecutorService multicastSenderThreadPool = null;


    public GrizzlyNetworkManager() {
    }
    public void localConfigure( final Map properties ) {
        maxPoolSize = Utility.getIntProperty( MAX_POOLSIZE.toString(), 50, properties );
        corePoolSize = Utility.getIntProperty( CORE_POOLSIZE.toString(), 20, properties );
        keepAliveTime = Utility.getLongProperty( KEEP_ALIVE_TIME.toString(), 60 * 1000, properties );
        poolQueueSize = Utility.getIntProperty( POOL_QUEUE_SIZE.toString(), 1024 * 4, properties );
        highWaterMark = Utility.getIntProperty( HIGH_WATER_MARK.toString(), 1024, properties );
        numberToReclaim = Utility.getIntProperty( NUMBER_TO_RECLAIM.toString(), 10, properties );
        virtualUriList = Utility.getStringProperty( VIRTUAL_MULTICAST_URI_LIST.toString(), null, properties );
    }

    @SuppressWarnings( "unchecked" )
    public synchronized void initialize( final String groupName, final String instanceName, final Map properties ) throws IOException {
        super.initialize(groupName, instanceName, properties);
        this.instanceName = instanceName;
        this.groupName = groupName;
        configure( properties );
        localConfigure(properties);
        System.out.println("Grizzly 1.9 NetworkManager");

        GMSContext ctx = GMSContextFactory.getGMSContext(groupName);
        if (ctx != null)  {
            GMSMonitor monitor = ctx.getGMSMonitor();
            if (monitor != null) {
                monitor.setSendWriteTimeout(this.sendWriteTimeout);
            }
        }

        // moved setting of localPeerId.

        InetAddress localInetAddress = null;
        if( host != null ) {
            localInetAddress = InetAddress.getByName( host );
        }
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig("GMS-GrizzlyControllerThreadPool-Group-" + groupName,
                corePoolSize,
                maxPoolSize,
                new ArrayBlockingQueue<Runnable>( poolQueueSize ),
                poolQueueSize,
                keepAliveTime,
                TimeUnit.MILLISECONDS,
                null,
                Thread.NORM_PRIORITY, //priority = 5
                null);

        execService = GrizzlyExecutorService.createInstance(threadPoolConfig);
        controller.setThreadPool( execService );

        final CacheableConnectorHandlerPool cacheableHandlerPool =
                new CacheableConnectorHandlerPool( controller, highWaterMark,
                numberToReclaim, maxParallelSendConnections,
                new ConnectionFinder<ConnectorHandler>() {

            @Override
            public ConnectorHandler find(ContactInfo<ConnectorHandler> cinfo,
                    Collection<ConnectorHandler> idleConnections,
                    Collection<ConnectorHandler> busyConnections)
                    throws IOException {

                if (!idleConnections.isEmpty()) {
                    return null;
                }

                return cinfo.createConnection();
            }
        });
        
        controller.setConnectorHandlerPool( cacheableHandlerPool );

        tcpSelectorHandler = new ReusableTCPSelectorHandler();
        tcpSelectorHandler.setPortRange(new PortRange(this.tcpStartPort, this.tcpEndPort));
        tcpSelectorHandler.setSelectionKeyHandler( new GrizzlyCacheableSelectionKeyHandler( highWaterMark, numberToReclaim, this ) );
        tcpSelectorHandler.setInet( localInetAddress );

        controller.addSelectorHandler( tcpSelectorHandler );

        MulticastSelectorHandler multicastSelectorHandler = new MulticastSelectorHandler();
        multicastSelectorHandler.setPort( multicastPort );
        multicastSelectorHandler.setSelectionKeyHandler( new GrizzlyCacheableSelectionKeyHandler( highWaterMark, numberToReclaim, this ) );
        if( GrizzlyUtil.isSupportNIOMulticast() ) {
            multicastSelectorHandler.setMulticastAddress( multicastAddress );
            multicastSelectorHandler.setNetworkInterface( networkInterfaceName );
            multicastSelectorHandler.setInet( localInetAddress );
            controller.addSelectorHandler( multicastSelectorHandler );                      
        }

        ProtocolChainInstanceHandler pciHandler = new DefaultProtocolChainInstanceHandler() {
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if( protocolChain == null ) {
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter( GrizzlyMessageProtocolParser.createParserProtocolFilter( null ) );
                    protocolChain.addFilter( new GrizzlyMessageDispatcherFilter( GrizzlyNetworkManager.this ) );
                }
                return protocolChain;
            }
        };
        controller.setProtocolChainInstanceHandler( pciHandler );
        SelectorFactory.setMaxSelectors( writeSelectorPoolSize );
    }

    private final CountDownLatch controllerGate = new CountDownLatch( 1 );
    private boolean controllerGateIsReady = false;
    private Throwable controllerGateStartupException = null;


    @Override
    @SuppressWarnings( "unchecked" )
    public synchronized void start() throws IOException {
        if( running )
            return;
        super.start();

        ControllerStateListener controllerStateListener = new ControllerStateListener() {

            public void onStarted() {
            }

            public void onReady() {
                if( LOG.isLoggable( Level.FINER ) )
                    LOG.log( Level.FINER, "GrizzlyNetworkManager is ready" );
                controllerGateIsReady = true;
                controllerGate.countDown();
            }

            public void onStopped() {
                controllerGate.countDown();
            }

            @Override
            public void onException(Throwable e) {
                if (controllerGate.getCount() > 0) {
                    getLogger().log(Level.SEVERE, "Exception during " +
                            "starting the controller", e);
                    controllerGate.countDown();
                    controllerGateStartupException = e;
                } else {
                    getLogger().log(Level.SEVERE, "Exception during " +
                            "controller processing", e);
                }
            }
        };
        controller.addStateListener( controllerStateListener );
        new Thread( controller ).start();
        long controllerStartTime = System.currentTimeMillis();
        try {
            controllerGate.await( startTimeout, TimeUnit.MILLISECONDS );
        } catch( InterruptedException e ) {
            e.printStackTrace();
        }
        long durationInMillis = System.currentTimeMillis() - controllerStartTime;

        // do not continue if controller did not start.
        if (!controller.isStarted() || !controllerGateIsReady) {
            if (controllerGateStartupException != null) {
                throw new IllegalStateException("Grizzly Controller was not started and ready after " + durationInMillis + " ms",
                        controllerGateStartupException);
            } else {
                throw new IllegalStateException("Grizzly Controller was not started and ready after " + durationInMillis + " ms");

            }
        } else if (controllerGateIsReady) {
            // todo: make this FINE in future.
            getLogger().config("Grizzly controller listening on " + tcpSelectorHandler.getInet() + ":" + tcpSelectorHandler.getPort() + ". Controller started in " + durationInMillis + " ms");
        }

        tcpPort = tcpSelectorHandler.getPort();

        if (localPeerID == null) {
            String uniqueHost = host;
            if (uniqueHost == null) {
                // prefer IPv4
                InetAddress firstInetAddress = NetworkUtility.getFirstInetAddress(false);
                if (firstInetAddress == null)
                    firstInetAddress = NetworkUtility.getFirstInetAddress(true);
                if (firstInetAddress == null)
                    throw new IOException("can not find a first InetAddress");
                uniqueHost = firstInetAddress.getHostAddress();
            }
            if (uniqueHost == null)
                throw new IOException("can not find an unique host");
            localPeerID = new PeerID<GrizzlyPeerID>(new GrizzlyPeerID(uniqueHost, tcpPort, multicastAddress, multicastPort), groupName, instanceName);
            peerIDMap.put(instanceName, localPeerID);
            if (LOG.isLoggable(Level.FINE))
                LOG.log(Level.FINE, "local peer id = " + localPeerID);
        }
        tcpSender = new GrizzlyTCPConnectorWrapper( controller, sendWriteTimeout, host, tcpPort, localPeerID );
        GrizzlyUDPConnectorWrapper udpConnectorWrapper = new GrizzlyUDPConnectorWrapper( controller,
                                                                                         sendWriteTimeout,
                                                                                         host,
                                                                                         multicastPort,
                                                                                         multicastAddress,
                                                                                         localPeerID );
        udpSender = udpConnectorWrapper;
        List<PeerID> virtualPeerIdList = getVirtualPeerIDList( virtualUriList );
        if( virtualPeerIdList != null && !virtualPeerIdList.isEmpty() ) {
            final boolean FAIRNESS = true;
            ThreadFactory tf = new GMSThreadFactory("GMS-mcastSenderThreadPool-thread");

            multicastSenderThreadPool = new ThreadPoolExecutor( 10, 10, 60 * 1000, TimeUnit.MILLISECONDS,
                                                                new ArrayBlockingQueue<Runnable>( 1024, FAIRNESS ), tf);
            multicastSender = new VirtualMulticastSender( host,
                                                          multicastAddress,
                                                          multicastPort,
                                                          networkInterfaceName,
                                                          multicastPacketSize,
                                                          localPeerID,
                                                          multicastSenderThreadPool,
                                                          this,
                                                          multicastTimeToLive,
                                                          virtualPeerIdList );
        } else {
            if( GrizzlyUtil.isSupportNIOMulticast() ) {
                multicastSender = udpConnectorWrapper;
            } else {
                final boolean FAIRNESS = true;
                ThreadFactory tf = new GMSThreadFactory("GMS-McastMsgProcessor-Group-" + groupName + "-thread");
                multicastSenderThreadPool = new ThreadPoolExecutor( 10, 10, 60 * 1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>( 1024, FAIRNESS ), tf );
                multicastSender = new BlockingIOMulticastSender( host,
                                                                 multicastAddress,
                                                                 multicastPort,
                                                                 networkInterfaceName,
                                                                 multicastPacketSize,
                                                                 localPeerID,
                                                                 multicastSenderThreadPool,
                                                                 multicastTimeToLive,
                                                                 this );
            }
        }
        if( tcpSender != null )
            tcpSender.start();
        if( udpSender != null )
            udpSender.start();
        if( multicastSender != null )
            multicastSender.start();
        addMessageListener( new PingMessageListener() );
        addMessageListener( new PongMessageListener() );
        running = true;
        }

    @Override
    public List<PeerID> getVirtualPeerIDList( String virtualUriList ) {
        if( virtualUriList == null )
            return null;
        LOG.config( "VIRTUAL_MULTICAST_URI_LIST = " + virtualUriList );
        List<PeerID> virtualPeerIdList = new ArrayList<PeerID>();
        //if this object has multiple addresses that are comma separated
        if( virtualUriList.indexOf( "," ) > 0 ) {
            String addresses[] = virtualUriList.split( "," );
            if( addresses.length > 0 ) {
                List<String> virtualUriStringList = Arrays.asList( addresses );
                for( String uriString : virtualUriStringList ) {
                    try {
                        PeerID peerID = getPeerIDFromURI( uriString );
                        if( peerID != null ) {
                            virtualPeerIdList.add( peerID );
                            LOG.config( "VIRTUAL_MULTICAST_URI = " + uriString + ", Converted PeerID = " + peerID );
                        }
                    } catch( URISyntaxException use ) {
                        if( LOG.isLoggable( Level.CONFIG ) )
                            LOG.log( Level.CONFIG, "failed to parse the virtual multicast uri(" + uriString + ")", use );
                    }
                }
            }
        } else {
            //this object has only one address in it, so add it to the list
            try {
                PeerID peerID = getPeerIDFromURI( virtualUriList );
                if( peerID != null ) {
                    virtualPeerIdList.add( peerID );
                    LOG.config( "VIRTUAL_MULTICAST_URI = " + virtualUriList + ", Converted PeerID = " + peerID );
                }
            } catch( URISyntaxException use ) {
                if( LOG.isLoggable( Level.CONFIG ) )
                    LOG.log( Level.CONFIG, "failed to parse the virtual multicast uri(" + virtualUriList + ")", use );
            }
        }
        return virtualPeerIdList;
    }

    private PeerID<GrizzlyPeerID> getPeerIDFromURI( String uri ) throws URISyntaxException {
        if( uri == null )
            return null;
        URI virtualUri = new URI( uri );
        return new PeerID<GrizzlyPeerID>( new GrizzlyPeerID( virtualUri.getHost(),
                                                             virtualUri.getPort(),
                                                             multicastAddress,
                                                             multicastPort ),
                                          localPeerID.getGroupName(),
                                          // the instance name is not meaningless in this case
                                          "Unknown" );
    }

    @Override
    public synchronized void stop() throws IOException {
        if( !running )
            return;
        running = false;
        super.stop();
        if( tcpSender != null )
            tcpSender.stop();
        if( udpSender != null )
            udpSender.stop();
        if( multicastSender != null )
            multicastSender.stop();
        if( multicastSenderThreadPool != null ) {
            multicastSenderThreadPool.shutdown();
        }
        peerIDMap.clear();
        selectionKeyMap.clear();
        pingMessageLockMap.clear();
        controller.stop();
        execService.shutdown();
    }

    public void beforeDispatchingMessage( MessageEvent messageEvent, Map piggyback ) {
        if( messageEvent == null )
            return;
        SelectionKey selectionKey = null;
        if( piggyback != null ) {
            Object value = piggyback.get( MESSAGE_SELECTION_KEY_TAG );
            if( value instanceof SelectionKey )
                selectionKey = (SelectionKey)value;
        }
        addRemotePeer( messageEvent.getSourcePeerID(), selectionKey );
    }

    public void afterDispatchingMessage( MessageEvent messageEvent, Map piggyback ) {
    }
}