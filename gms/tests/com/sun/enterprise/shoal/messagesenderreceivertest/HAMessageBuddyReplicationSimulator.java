/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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

/*
 *
 * This program creates a cluster conprised of a master and N number of core members.
 * Each core member acts as an instance in the cluster which replicates(sends messages)
 * to the instance that is one greater than itself. The last instance replicates
 * to the first instance in the cluster. The names of the instances are instance101,
 * instance102, etc... The master node is called master. Based on the arguments
 * passed into the program the instances will send M objects*N messages to the replica.
 * The replica saves the messages and verifies that the number of objects/messages
 * were received and that the content was correct. Once each instance is done
 * sending their messages a final message (DONE) is sent to the replica. The replica
 * upon receiving the message forwards this message to the master. Once the master
 * has received a DONE message from each instance, it calls group shutdown on the cluster.
 *
 */
package com.sun.enterprise.shoal.messagesenderreceivertest;

import com.sun.enterprise.ee.cms.impl.base.Utility;
import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.JoinedAndReadyNotificationActionFactoryImpl;
import com.sun.enterprise.ee.cms.impl.client.MessageActionFactoryImpl;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HAMessageBuddyReplicationSimulator {

    static final int EXCEEDTIMEOUTLIMIT = 5;  // in minutes

    private GroupManagementService gms = null;
    private static final Logger gmsLogger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);
    // private static final Logger gmsLogger = HASimulatorLogger.getLogger(HASimulatorLogger.HAMSGSimulator_LOGGER);
    static ConcurrentHashMap<Integer, ConcurrentHashMap<String, String>> msgIDs_received = new ConcurrentHashMap<Integer, ConcurrentHashMap<String, String>>();
    static ConcurrentHashMap<Integer, ConcurrentHashMap<Long, String>> payloads_received = new ConcurrentHashMap<Integer, ConcurrentHashMap<Long, String>>();
    static String memberID = null;
    static Integer memberIDNum = new Integer(0);
    static int numberOfInstances = 0;
    static int payloadSize = 0;
    static int numberOfObjects = 0;
    static int numberOfMsgsPerObject = 0;
    static AtomicInteger numberOfJoinAndReady = new AtomicInteger(0);
    static List<String> receivedDoneMsgFrom = new ArrayList<String>();
    static Calendar sendStartTime = null;
    static Calendar sendEndTime = null;
    static Calendar receiveStartTime = null;
    static Calendar receiveEndTime = null;
    static AtomicBoolean firstMsgReceived = new AtomicBoolean(false);
    static List<String> members;
    static List<Integer> memberIDs;
    static String groupName = null;
    static AtomicBoolean groupShutdown = new AtomicBoolean(false);
    static int replica = 0;
    static int minInstanceNum = 101;
    // Setting this value to true will result in the Sending and Receiving messages
    // to be displayed without having to turn FINE logging on and potentially changing
    // the timing of the test to a large degree.
    private static final boolean VERBOSE = false;

    public static void main(String[] args) {

        //for (int z = 0; z < args.length; z++) {
        //    gmsLogger.log(Level.INFO,(z + "=" + args[z]);
        //}

        if (args[0].equalsIgnoreCase("-h")) {
            usage();
        }

        if (args[0].equalsIgnoreCase("master")) {
            if (args.length != 3) {
                usage();
            }
            memberID = args[0];
            gmsLogger.log(Level.INFO, ("memberID=" + memberID));
            groupName = args[1];
            gmsLogger.log(Level.INFO, ("GroupName=" + groupName));
            numberOfInstances = Integer.parseInt(args[2]);
            gmsLogger.log(Level.INFO, ("numberOfInstances=" + numberOfInstances));

        } else if (args[0].contains("instance")) {
            if (args.length == 6) {
                memberID = args[0];
                if (!memberID.startsWith("instance")) {
                    System.err.println("ERROR: The member name must be in the format 'instancexxx'");
                    System.exit(1);
                }
                gmsLogger.log(Level.INFO, ("memberID=" + memberID));
                memberIDNum = new Integer(memberID.replace("instance", ""));
                groupName = args[1];
                gmsLogger.log(Level.INFO, ("GroupName=" + groupName));
                numberOfInstances = Integer.parseInt(args[2]);
                gmsLogger.log(Level.INFO, ("numberOfInstances=" + numberOfInstances));
                numberOfObjects = Integer.parseInt(args[3]);
                gmsLogger.log(Level.INFO, ("numberOfObjects=" + numberOfObjects));
                numberOfMsgsPerObject = Integer.parseInt(args[4]);
                gmsLogger.log(Level.INFO, ("numberOfMsgsPerObject=" + numberOfMsgsPerObject));
                payloadSize = Integer.parseInt(args[5]);
                gmsLogger.log(Level.INFO, ("payloadSize=" + payloadSize));
            } else {
                usage();
            }
        } else {
            usage();
        }
        Utility.setLogger(gmsLogger);
        Utility.setupLogHandler();

        /*
        replica = (int) ((numberOfInstances * Math.random()) + 101);
        if (replica == memberIDNum) {
        replica++;
        // if the replica number is great than the number of instances
        // wrap around to the first instance
        if (replica > ((minInstanceNum + numberOfInstances) - 1)) {
        replica = minInstanceNum;
        }
        }

         */
        replica = memberIDNum + 1;
        if (replica > ((minInstanceNum + numberOfInstances) - 1)) {
            replica = minInstanceNum;
        }
        gmsLogger.log(Level.INFO, ("Replica= instance" + replica));


        HAMessageBuddyReplicationSimulator sender = new HAMessageBuddyReplicationSimulator();

        sender.test();

        sender.waitTillDone();

        // only do verification for INSTANCES
        if (!memberID.equalsIgnoreCase("master")) {
            System.out.println("Checking to see if correct number of messages (Objects:" + numberOfObjects + ", NumberOfMsg:" + numberOfMsgsPerObject + " = [" + (numberOfObjects * numberOfMsgsPerObject) + "])  were received from each instance");

            //gmsLogger.log(Level.INFO,("chm.size()=" + chm.size());
            System.out.println("================================================================");
            Enumeration eNum = msgIDs_received.keys();
            while (eNum.hasMoreElements()) {
                Integer instanceNum = (Integer) eNum.nextElement();
                System.out.println("checking instance [" + instanceNum + "] in msgIDs_received");

                int droppedMessages = 0;
                int payloadErrors = 0;

                ConcurrentHashMap<String, String> msgIDs = msgIDs_received.get(instanceNum);
                //System.out.println("msgIDs=" + msgIDs.toString());
                String key = null;
                if ((msgIDs != null) && !msgIDs.isEmpty()) {
                    for (long objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
                        for (long msgId = 1; msgId <= numberOfMsgsPerObject; msgId++) {
                            key = objectNum + ":" + msgId;
                            if (!msgIDs.containsKey(key)) {
                                droppedMessages++;
                                System.out.println("Never received objectId:" + objectNum + " msgId:" + msgId + ", from:" + instanceNum);
                            }
                        }
                    }
                } else {
                    droppedMessages = -1;
                }
                System.out.println("---------------------------------------------------------------");
                if (droppedMessages == 0) {
                    System.out.println(instanceNum + ": PASS.  No dropped messages");
                } else if (droppedMessages == -1) {
                    System.out.println(instanceNum + ": FAILED. No message IDs were received");
                } else {
                    System.out.println(instanceNum + ": FAILED. Confirmed (" + droppedMessages + ") messages were dropped");
                }
                System.out.println("================================================================");

                /* header format is:
                 *  long objectID
                long msgID
                int to
                int from
                 */
                int tmpSize = payloadSize - (8 + 8 + 4 + 4);
                String expectedPayload = new String(createPayload(tmpSize));
                //gmsLogger.log(Level.INFO,("expected Payload" + expectedPayload);

                ConcurrentHashMap<Long, String> payLoads = payloads_received.get(instanceNum);

                for (long objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
                    if (!payLoads.containsKey(objectNum)) {
                        payloadErrors++;
                        System.out.println("INTERNAL ERROR: objectId:" + objectNum + " from:" + instanceNum + " missing from payload structure");
                    } else {
                        String payLoad = payLoads.get(objectNum);
                        if (!payLoad.equals(expectedPayload)) {
                            System.out.println("actual Payload[objectNum]:" + payLoad);
                            payloadErrors++;
                            System.out.println("Payload did not match for objectId:" + objectNum + ", from:" + instanceNum);
                        }
                    }


                }
                System.out.println("---------------------------------------------------------------");

                if (payloadErrors == 0) {
                    System.out.println(instanceNum + ": PASS.  No payload errors");
                } else {
                    System.out.println(instanceNum + ": FAILED. Confirmed (" + payloadErrors + ") payload errors");
                }
                System.out.println("================================================================");


            }
            long timeDelta = 0;
            long remainder = 0;
            long msgPerSec = 0;
            // NOTE
            // The receive time could be quicker than the send time since we might be a little
            // slower at sending than we are receiving the message and the other members in the
            // cluster could be finished before us
            if (sendEndTime != null && sendStartTime != null) {
                timeDelta = (sendEndTime.getTimeInMillis() - sendStartTime.getTimeInMillis()) / 1000;
                remainder = (sendEndTime.getTimeInMillis() - sendStartTime.getTimeInMillis()) % 1000;
                if (timeDelta == 0) {
                    msgPerSec = 0;
                } else {
                    msgPerSec = (numberOfObjects * numberOfMsgsPerObject * numberOfInstances) / timeDelta;
                }
                System.out.println("\nSending Messages Time data: Start[" + sendStartTime.getTime() + "], End[" + sendEndTime.getTime() + "], Delta[" + timeDelta + "." + remainder + "] secs, MsgsPerSec[" + msgPerSec + "]\n");
            }
            if (receiveEndTime != null && receiveStartTime != null) {
                timeDelta = (receiveEndTime.getTimeInMillis() - receiveStartTime.getTimeInMillis()) / 1000;
                remainder = (receiveEndTime.getTimeInMillis() - receiveStartTime.getTimeInMillis()) % 1000;
                if (timeDelta == 0) {
                    msgPerSec = 0;
                } else {
                    msgPerSec = (numberOfObjects * numberOfMsgsPerObject * numberOfInstances) / timeDelta;
                }
                System.out.println("\nReceiving Messages Time data: Start[" + receiveStartTime.getTime() + "], End[" + receiveEndTime.getTime() + "], Delta[" + timeDelta + "." + remainder + "] secs, MsgsPerSec[" + msgPerSec + "]\n");
            }
        }


        System.out.println("================================================================");
        gmsLogger.log(Level.INFO, "Testing Complete");

    }

    public static void usage() {

        System.out.println(" For master:");
        System.out.println("    <memberid(master)> <groupName> <number_of_instances>");
        System.out.println(" For instances:");
        System.out.println("    <memberid(instancexxx)> <groupName> <number_of_instances> <number_of_objects> <number_of_msgs_per_object> <payloads_receivedize>");
        System.exit(0);
    }

    private void test() {

        gmsLogger.log(Level.INFO, "Testing Started");



        //initialize Group Management Service and register for Group Events

        gmsLogger.log(Level.INFO, "Registering for group event notifications");
        if (memberID.equalsIgnoreCase("master")) {
            gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.SPECTATOR);
        } else {
            gms = initializeGMS(memberID, groupName, GroupManagementService.MemberType.CORE);
        }
        gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(new JoinAndReadyNotificationCallBack(memberID)));
        gms.addActionFactory(new MessageActionFactoryImpl(new MessageCallBack(memberID)), "TestComponent");

        gmsLogger.log(Level.INFO, "Joining Group " + groupName);
        try {
            gms.join();
        } catch (GMSException e) {
            gmsLogger.log(Level.SEVERE, "Exception occured :" + e, e);
            System.exit(1);
        }

        sleep(5000);



        gms.reportJoinedAndReadyState(groupName);
        sleep(5000);

        gmsLogger.log(Level.INFO, "Waiting for all members to joined the group:" + groupName);

        if (!memberID.equalsIgnoreCase("master")) {
            gmsLogger.log(Level.INFO, "Waiting for all members to report joined and ready for the group:" + groupName);

            while (true) {

                sleep(2000);


                gmsLogger.log(Level.INFO, ("numberOfJoinAndReady=" + numberOfJoinAndReady.get()));
                gmsLogger.log(Level.INFO, ("numberOfInstances=" + numberOfInstances));

                if (numberOfJoinAndReady.get() == numberOfInstances) {
                    members = gms.getGroupHandle().getCurrentCoreMembers();
                    System.out.println("===================================================");
                    gmsLogger.log(Level.INFO, "All members are joined and ready in the group:" + groupName);
                    gmsLogger.log(Level.INFO, "Members are:" + members.toString());
                    System.out.println("===================================================");
                    members.remove(memberID);
                    memberIDs = new ArrayList<Integer>();
                    for (String instanceName : members) {
                        Integer instanceNum = new Integer(instanceName.replace("instance", ""));
                        memberIDs.add(instanceNum);
                    }
                    break;
                }

            }
            gmsLogger.log(Level.INFO, "Starting Testing");

            gmsLogger.log(Level.INFO, "Sending Messages to:" + replica);

            byte[] msg = new byte[1];

            sendStartTime = new GregorianCalendar();
            gmsLogger.log(Level.INFO, "Send start time: " + sendStartTime);


            for (long objectNum = 1; objectNum <= numberOfObjects; objectNum++) {
                gmsLogger.log(Level.INFO, "Sending Object:" + objectNum + " to:" + replica);

                for (long msgNum = 1; msgNum <= numberOfMsgsPerObject; msgNum++) {


                    // create a unique objectnum
                    //String sObject = Integer.toString(memberIDNum) + Long.toString(objectNum);
                    //long _objectNum = Long.parseLong(sObject);

                    // create the message to be sent
                    msg = createMsg(objectNum, msgNum, replica, memberIDNum, payloadSize);

                    gmsLogger.log(Level.FINE, "Sending Message:" + displayMsg(msg));
                    if (VERBOSE) {
                        System.out.println("Sending Message:" + displayMsg(msg));
                    }

                    try {
                        gms.getGroupHandle().sendMessage("instance" + replica, "TestComponent", msg);
                    } catch (GMSException ge) {
                        if (!ge.getMessage().contains("Client is busy or timed out")) {
                            gmsLogger.log(Level.WARNING, "Exception occured sending message (object:" + objectNum + ",MsgID:" + msgNum + " to :instance" + replica + "):" + ge, ge);
                        } else {
                            //retry the send up to 3 times
                            for (int i = 1; i <= 3; i++) {
                                try {
                                    sleep(10);
                                    gmsLogger.log(Level.WARNING, "Retry [" + i + "] time(s) to send message (object:" + objectNum + ",MsgID:" + msgNum + " to :instance" + replica + ")");
                                    gms.getGroupHandle().sendMessage("instance" + replica, "TestComponent", msg);
                                    break; // if successful
                                } catch (GMSException ge1) {
                                    gmsLogger.log(Level.FINE, "\n-----------------------------\nException occured during send message retry (" + i + ") for (object:" + objectNum + ",MsgID:" + msgNum + " to :instance" + replica + "):" + ge1, ge1);
                                }
                            }
                        }
                    }


                    // think time
                    sleep(10);

                }

            }

            sendEndTime = new GregorianCalendar();
            gmsLogger.log(Level.INFO, "Send end time: " + sendEndTime);

            gmsLogger.log(Level.INFO, "Finished Sending Messages to:" + replica);
            // send donesending message out
            String doneMsg = "DONE:" + memberID;
            gmsLogger.log(Level.INFO, "Sending DONE message to replica[" + replica + "]:" + doneMsg);
            try {
                gms.getGroupHandle().sendMessage("instance" + replica, "TestComponent", doneMsg.getBytes());
            } catch (GMSException e) {
                gmsLogger.log(Level.WARNING, "Exception occured sending DONE message to replica" + e, e);
                //retry the send up to 3 times
                for (int i = 1; i <= 3; i++) {
                    try {
                        sleep(10);
                        gmsLogger.log(Level.WARNING, "Retry [" + i + "] time(s) to send message (" + doneMsg + ")");
                        gms.getGroupHandle().sendMessage("instance" + replica, "TestComponent", doneMsg.getBytes());
                        break; // if successful
                    } catch (GMSException ge1) {
                        gmsLogger.log(Level.FINE, "\n-----------------------------\nException occured while resending DONE message retry (" + i + ") for (" + doneMsg + ") to replica:" + replica + " : " + ge1, ge1);
                    }
                }
            }


        }

    }

    public void waitTillDone() {
        long waitForStartTime = 0;
        long exceedTimeout = 0;
        boolean firstTime = true;
        long currentTime = 0;

        if (memberID.equalsIgnoreCase("master")) {
            gmsLogger.log(Level.INFO, "===================================================");
            gmsLogger.log(Level.INFO, "Waiting for all CORE members to send DONE messages");
            gmsLogger.log(Level.INFO, ("exceed timeout limit=" + EXCEEDTIMEOUTLIMIT));

            while (true) {
                // wait for all instances to forward the DONE message that they received to us
                if ((receivedDoneMsgFrom.size() == numberOfInstances)) {
                    break;
                } else if ((receivedDoneMsgFrom.size() > 0)) {
                    if (firstTime) {
                        waitForStartTime = System.currentTimeMillis();
                        firstTime = false;
                    }
                    currentTime = System.currentTimeMillis();
                    exceedTimeout = ((currentTime - waitForStartTime) / 60000);
                    gmsLogger.log(Level.INFO, ("current exceed timeout=" + exceedTimeout));

                    if (exceedTimeout >= EXCEEDTIMEOUTLIMIT - 1) {
                        gmsLogger.log(Level.SEVERE, memberID + " EXCEEDED " + EXCEEDTIMEOUTLIMIT + " minute timeout waiting to receive all DONE messages");
                        break;
                    }
                }
                gmsLogger.log(Level.INFO, "Waiting 10 seconds for instances to forward their DONE messages to us...");
                gmsLogger.log(Level.INFO, ("Received DONE from: " + receivedDoneMsgFrom.toString() + "[" + receivedDoneMsgFrom.size() + "] so far"));
                sleep(10000); // 10 seconds

            }
            gms.announceGroupShutdown(groupName, GMSConstants.shutdownState.INITIATED);
        } else {
            // instance
            while (!gms.isGroupBeingShutdown(groupName)) {
                gmsLogger.log(Level.INFO, "Waiting for group shutdown...");
                sleep(10000);
            }
            gmsLogger.log(Level.INFO, ("Completed processing, Group Shutdown is has begun"));
        }
        leaveGroupAndShutdown(memberID, gms);
    }

    private GroupManagementService initializeGMS(String memberID, String groupName, GroupManagementService.MemberType mType) {
        gmsLogger.log(Level.INFO, "Initializing Shoal for member: " + memberID + " group:" + groupName);
        Properties configProps = new Properties();
        configProps.put(ServiceProviderConfigurationKeys.MULTICASTADDRESS.toString(),
                System.getProperty("MULTICASTADDRESS", "229.9.1.4"));
        configProps.put(ServiceProviderConfigurationKeys.MULTICASTPORT.toString(), 2299);
//        gmsLogger.FINE("Is initial host="+System.getProperty("IS_INITIAL_HOST"));
        configProps.put(ServiceProviderConfigurationKeys.IS_BOOTSTRAPPING_NODE.toString(),
                System.getProperty("IS_INITIAL_HOST", "false"));
        /*
        if(System.getProperty("INITIAL_HOST_LIST") != null){
        configProps.put(ServiceProviderConfigurationKeys.VIRTUAL_MULTICAST_URI_LIST.toString(),
        System.getProperty("INITIAL_HOST_LIST"));
        }
         */
        configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_RETRIES.toString(),
                System.getProperty("MAX_MISSED_HEARTBEATS", "3"));
        configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_TIMEOUT.toString(),
                System.getProperty("HEARTBEAT_FREQUENCY", "2000"));
        //Uncomment this to receive loop back messages
        //configProps.put(ServiceProviderConfigurationKeys.LOOPBACK.toString(), "true");
        final String bindInterfaceAddress = System.getProperty("BIND_INTERFACE_ADDRESS");
        if (bindInterfaceAddress != null) {
            configProps.put(ServiceProviderConfigurationKeys.BIND_INTERFACE_ADDRESS.toString(), bindInterfaceAddress);
        }

        return (GroupManagementService) GMSFactory.startGMSModule(
                memberID,
                groupName,
                mType,
                configProps);
    }

    private void leaveGroupAndShutdown(String memberID, GroupManagementService gms) {
        gmsLogger.log(Level.INFO, "Shutting down gms " + gms + "for member: " + memberID);
        gms.shutdown(GMSConstants.shutdownType.INSTANCE_SHUTDOWN);
    }

    private class JoinAndReadyNotificationCallBack implements CallBack {

        private String memberID;

        public JoinAndReadyNotificationCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            gmsLogger.log(Level.INFO, "***JoinAndReadyNotification received from: " + notification.getMemberToken());
            if (!(notification instanceof JoinedAndReadyNotificationSignal)) {
                gmsLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                if (!notification.getMemberToken().equals("master")) {

                    // determine how many core members are ready to begin testing
                    JoinedAndReadyNotificationSignal readySignal = (JoinedAndReadyNotificationSignal) notification;
                    List<String> currentCoreMembers = readySignal.getCurrentCoreMembers();
                    numberOfJoinAndReady.set(0);
                    for (String instanceName : currentCoreMembers) {
                        MemberStates state = gms.getGroupHandle().getMemberState(instanceName, 6000, 3000);
                        switch (state) {
                            case READY:
                            case ALIVEANDREADY:
                                numberOfJoinAndReady.getAndIncrement();
                            default:
                        }
                    }

                    gmsLogger.log(Level.INFO, "numberOfJoinAndReady received so far is: " + numberOfJoinAndReady.get());
                }
            }
        }
    }

    private class MessageCallBack implements CallBack {

        private String memberID;

        public MessageCallBack(String memberID) {
            this.memberID = memberID;
        }

        public void processNotification(Signal notification) {
            // gmsLogger.log(Level.INFO, "***Message received from: " + notification.getMemberToken());
            if (!(notification instanceof MessageSignal)) {
                gmsLogger.log(Level.SEVERE, "received unknown notification type:" + notification + " from:" + notification.getMemberToken());
            } else {
                synchronized (this) {
                    if (!memberID.equalsIgnoreCase("master")) {
                        try {
                            notification.acquire();
                            MessageSignal messageSignal = (MessageSignal) notification;
                            if (!firstMsgReceived.get()) {
                                firstMsgReceived.set(true);
                                receiveStartTime = new GregorianCalendar();
                                gmsLogger.log(Level.INFO, "Receive start time: " + receiveStartTime);

                            }

                            String msgString = new String(messageSignal.getMessage());
                            // is this a done message
                            if (msgString.contains("DONE")) {
                                // get who it is from
                                int index = msgString.indexOf(":");
                                String from = msgString.substring(index + 1);
                                gmsLogger.log(Level.INFO, "Received DONE message from: " + from);
                                // forward this message onto the master
                                gmsLogger.log(Level.INFO, "Forward DONE message to master:" + msgString);
                                try {
                                    gms.getGroupHandle().sendMessage("master", "TestComponent", msgString.getBytes());
                                } catch (GMSException e) {
                                    gmsLogger.log(Level.WARNING, "Exception occured while forwording DONE message to master" + e, e);
                                    //retry the send up to 3 times
                                    for (int i = 1; i <= 3; i++) {
                                        try {
                                            sleep(10);
                                            gmsLogger.log(Level.WARNING, "Retry [" + i + "] time(s) to send message (" + msgString + ")");
                                            gms.getGroupHandle().sendMessage("master", "TestComponent", msgString.getBytes());
                                            break; // if successful
                                        } catch (GMSException ge1) {
                                            gmsLogger.log(Level.SEVERE, "Exception occured while forwording DONE message: retry (" + i + ") for (" + msgString + ") to master" + ge1, ge1);
                                        }
                                    }
                                }


                                if (!receivedDoneMsgFrom.contains(from)) {
                                    receivedDoneMsgFrom.add(from);
                                }

                                // if we have received a DONE message for each of the instances
                                // that are sending to us then stop timing
                                if ((receivedDoneMsgFrom.size() == msgIDs_received.size())) {
                                    receiveEndTime = new GregorianCalendar();
                                    gmsLogger.log(Level.INFO, "Receive end time: " + receiveEndTime);

                                }
                            } else {
                                // it is not a done message so process it
                                final byte[] msg = msgString.getBytes();

                                ByteBuffer buf = ByteBuffer.wrap(msg);
                                long objectID = buf.getLong(0);
                                long msgID = buf.getLong(8);
                                int to = buf.getInt(16);
                                int from = buf.getInt(20);
                                String payload = new String(msg, 24, msg.length - 24);

                                String shortPayLoad = payload;
                                if (shortPayLoad.length() > 10) {
                                    shortPayLoad = shortPayLoad.substring(0, 10) + "..." + shortPayLoad.substring(shortPayLoad.length() - 10, shortPayLoad.length());
                                }
                                gmsLogger.log(Level.FINE, memberID + " Received msg:" + displayMsg(msg));
                                if (VERBOSE) {
                                    System.out.println(" Received msg:" + displayMsg(msg));
                                }
                                if (msgID > 0) {


                                    // keep track of the objectIDs
                                    // if the INSTANCE does not exist in the map, create it.
                                    ConcurrentHashMap<Long, String> object = payloads_received.get(from);
                                    if (object == null) {
                                        object = new ConcurrentHashMap<Long, String>();
                                    }
                                    object.put(objectID, payload);
                                    payloads_received.put(from, object);


                                    // keep track of the msgIDs
                                    // if the INSTANCE does not exist in the map, create it.
                                    ConcurrentHashMap<String, String> object_MsgIDs = msgIDs_received.get(from);
                                    if (object_MsgIDs == null) {
                                        object_MsgIDs = new ConcurrentHashMap<String, String>();
                                    }

                                    object_MsgIDs.put(objectID + ":" + msgID, "");
                                    msgIDs_received.put(from, object_MsgIDs);
                                }
                            }
                        } catch (SignalAcquireException e) {
                            e.printStackTrace();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {
                            try {
                                notification.release();
                            } catch (Exception e) {
                            }

                        }
                    } else {
                        try {
                            // this is the master
                            notification.acquire();
                            MessageSignal messageSignal = (MessageSignal) notification;

                            String msgString = new String(messageSignal.getMessage());
                            if (msgString.contains("DONE")) {
                                int index = msgString.indexOf(":");
                                String from = msgString.substring(index + 1);
                                gmsLogger.log(Level.INFO, "Received DONE message:" + msgString);
                                // since we can received multiple DONE messages from the
                                // same member only count them once
                                if (!receivedDoneMsgFrom.contains(from)) {
                                    receivedDoneMsgFrom.add(from);
                                }
                            }

                        } catch (SignalAcquireException e) {
                            e.printStackTrace();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {
                            try {
                                notification.release();
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        }
    }

    public static byte[] long2bytearray(long l) {
        byte b[] = new byte[8];
        ByteBuffer buf = ByteBuffer.wrap(b);
        buf.putLong(l);
        return b;
    }

    public static byte[] int2bytearray(int i) {
        byte b[] = new byte[4];
        ByteBuffer buf = ByteBuffer.wrap(b);
        buf.putInt(i);
        return b;
    }

    public static byte[] createMsg(long objectID, long msgID, int to, int from, int payloads_receivedize) {

        // create the message to be sent
        byte[] b1 = long2bytearray(objectID);
        byte[] b2 = long2bytearray(msgID);
        byte[] b3 = int2bytearray(to);
        byte[] b4 = int2bytearray(from);
        byte[] b5 = new byte[1];


        int pls = payloads_receivedize - (b1.length + b2.length + b3.length + b4.length);
        b5 = createPayload(pls);

        int msgSize = b1.length + b2.length + b3.length + b4.length + b5.length;

        byte[] msg = new byte[(int) msgSize];
        int j = 0;
        for (int i = 0; i < b1.length; i++) {
            msg[j++] = b1[i];
        }
        for (int i = 0; i < b2.length; i++) {
            msg[j++] = b2[i];
        }
        for (int i = 0; i < b3.length; i++) {
            msg[j++] = b3[i];
        }
        for (int i = 0; i < b4.length; i++) {
            msg[j++] = b4[i];
        }
        for (int i = 0; i < b5.length; i++) {
            msg[j++] = b5[i];
        }
        return msg;
    }

    public static String displayMsg(byte[] msg) {
        StringBuffer sb = new StringBuffer(60);
        ByteBuffer buf = ByteBuffer.wrap(msg);
        /*
        long objectID = buf.getLong(0);
        long msgID = buf.getLong(8);
        int to = buf.getInt(16);
        int from = buf.getInt(20);
        String payload = new String(msg, 24, msg.length - 24);
         */
        sb.append("[");
        sb.append("objectId:").append(buf.getLong(0));
        sb.append(" msgId:").append(buf.getLong(8));
        sb.append(" to:").append(buf.getInt(16));
        sb.append(" from:").append(buf.getInt(20));
        String payload = new String(msg, 24, msg.length - 24);
        if (payload.length() > 25) {
            sb.append(" payload:").append(payload.substring(0, 10)).append("...");
            sb.append(payload.substring(payload.length() - 10, payload.length()));
            //sb.append(" payload:").append(payload);
            sb.append(" payload length:").append(Integer.toString(payload.length()));
        } else {
            sb.append(" payload:").append(payload);
        }
        sb.append("]");

        return sb.toString();
    }

    public static byte[] createPayload(int size) {
        byte[] b = new byte[1];

        b = new byte[size];
        b[0] = 'a';
        int k = 1;
        for (; k < size - 1; k++) {
            b[k] = 'X';
        }
        b[k] = 'z';

        return b;
    }

    public static void sleep(long i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException ex) {
        }
    }
}
