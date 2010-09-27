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

package org.shoal.ha.cache.api;

import org.shoal.ha.cache.impl.store.ReplicaStore;
import org.shoal.ha.group.GroupService;
import org.shoal.ha.mapper.KeyMapper;
import org.shoal.ha.cache.impl.command.CommandManager;
import org.shoal.ha.cache.impl.util.ResponseMediator;

/**
 * @author Mahesh Kannan
 */
public class DataStoreContext<K, V> {

    private String serviceName;

    private String instanceName;

    private String groupName;

    private DataStoreKeyHelper<K> dataStoreKeyHelper;

    private DataStoreEntryHelper<K, V> dataStoreEntryHelper;

    private CommandManager<K, V> cm;

    private KeyMapper keyMapper;

    private ResponseMediator responseMediator;

    private GroupService groupService;

    private ReplicaStore<K, V> replica;

    private boolean cacheLocally;

    private boolean doSynchronousReplication;

    private IdleEntryDetector<K, V> idleDetector;

    public DataStoreContext(String serviceName, GroupService gs, ClassLoader loader) {
        this.serviceName = serviceName;
        this.groupService = gs;
        this.instanceName = gs.getMemberName();
        this.groupName = gs.getGroupName();
        this.cm = new CommandManager<K, V>(this);
        this.responseMediator = new ResponseMediator();

        replica = new ReplicaStore<K, V>(this);
        if (idleDetector != null) {
            replica.setIdleEntryDetector(idleDetector);
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getGroupName() {
        return groupName;
    }

    public DataStoreKeyHelper<K> getDataStoreKeyHelper() {
        return dataStoreKeyHelper;
    }

    public void setDataStoreKeyHelper(DataStoreKeyHelper<K> dataStoreKeyHelper) {
        this.dataStoreKeyHelper = dataStoreKeyHelper;
    }

    public DataStoreEntryHelper<K, V> getDataStoreEntryHelper() {
        return dataStoreEntryHelper;
    }

    public void setDataStoreEntryHelper(DataStoreEntryHelper<K, V> dataStoreEntryHelper) {
        this.dataStoreEntryHelper = dataStoreEntryHelper;
    }

    public KeyMapper getKeyMapper() {
        return keyMapper;
    }

    public void setKeyMapper(KeyMapper keyMapper) {
        this.keyMapper = keyMapper;
    }

    public CommandManager<K, V> getCommandManager() {
        return cm;
    }

    public ResponseMediator getResponseMediator() {
        return responseMediator;
    }

    public GroupService getGroupService() {
        return groupService;
    }

    public ReplicaStore<K, V> getReplicaStore() {
        return replica;
    }

    public boolean isCacheLocally() {
        return cacheLocally;
    }

    public void setCacheLocally(boolean cacheLocally) {
        this.cacheLocally = cacheLocally;
    }

    public boolean isDoSynchronousReplication() {
        return doSynchronousReplication;
    }

    public void setDoSyncReplication(boolean doSyncReplication) {
        this.doSynchronousReplication = doSyncReplication;
        System.out.println("**DataStorecontext.setDoSyncReplication = " + doSyncReplication);
    }
}
