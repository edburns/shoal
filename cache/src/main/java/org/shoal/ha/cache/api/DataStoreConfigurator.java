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

import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.store.DataStoreEntryUpdater;
import org.shoal.ha.mapper.KeyMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mahesh Kannan
 */
public class DataStoreConfigurator<K, V> {

    private String instanceName;

    private String groupName;

    private String storeName;

    private Class<K> keyClazz;

    private Class<V> valueClazz;

    private KeyMapper keyMapper;

    private boolean startGMS;

    private ClassLoader clazzLoader;

    private boolean cacheLocally;

    private boolean doSynchronousReplication;

    private List<Command<K, ? super V>> commands = new ArrayList<Command<K, ? super V>>();

    private List<AbstractCommandInterceptor<K, V>> interceptors;

    private boolean addCommands;

    private IdleEntryDetector<K, V> idleEntryDetector;

    private long defaultMaxIdleTimeInMillis = -1;

    private DataStoreEntryUpdater<K, V> dseUpdater;

    private boolean safeToDelayCaptureState = true;

    private boolean useMapToCacheCommands = true;

    protected DataStoreConfigurator() {

    }

    public String getInstanceName() {
        return instanceName;
    }

    public DataStoreConfigurator<K, V> setInstanceName(String instanceName) {
        this.instanceName = instanceName;
        return this;
    }

    public String getGroupName() {
        return groupName;
    }

    public DataStoreConfigurator<K, V> setGroupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public String getStoreName() {
        return storeName;
    }

    public DataStoreConfigurator<K, V> setStoreName(String storeName) {
        this.storeName = storeName;
        return this;
    }

    public Class<K> getKeyClazz() {
        return keyClazz;
    }

    public DataStoreConfigurator<K, V> setKeyClazz(Class<K> kClazz) {
        this.keyClazz = kClazz;
        return this;
    }

    public Class<V> getValueClazz() {
        return valueClazz;
    }

    public DataStoreConfigurator<K, V> setValueClazz(Class<V> vClazz) {
        this.valueClazz = vClazz;
        return this;
    }

    public KeyMapper getKeyMapper() {
        return keyMapper;
    }

    public DataStoreConfigurator<K, V> setKeyMapper(KeyMapper keyMapper) {
        this.keyMapper = keyMapper;
        return this;
    }
    public boolean isStartGMS() {
        return startGMS;
    }

    public DataStoreConfigurator<K, V> setStartGMS(boolean startGMS) {
        this.startGMS = startGMS;
        return this;
    }

    public ClassLoader getClassLoader() {
        return clazzLoader;
    }

    public DataStoreConfigurator<K, V> setClassLoader(ClassLoader loader) {
        this.clazzLoader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
        return this;
    }

    public boolean isCacheLocally() {
        return cacheLocally;
    }

    public DataStoreConfigurator<K, V> setCacheLocally(boolean cacheLocally) {
        this.cacheLocally = cacheLocally;
        return this;
    }

    public boolean isDoSynchronousReplication() {
        return doSynchronousReplication;
    }

    public DataStoreConfigurator<K, V> setDoSynchronousReplication(boolean val) {
        this.doSynchronousReplication = val;
        return this;
    }

    public List<Command<K, ? super V>> getCommands() {
        return commands;
    }

    public void addCommand(Command<K, V> cmd) {
        commands.add(cmd);
    }

    public DataStoreConfigurator<K, V> setDoAddCommands() {
        addCommands = true;
        return this;
    }

    public boolean isDoAddCommands() {
        return addCommands;
    }

    public IdleEntryDetector<K, V> getIdleEntryDetector() {
        return idleEntryDetector;
    }

    public DataStoreConfigurator<K, V> setIdleEntryDetector(IdleEntryDetector<K, V> idleEntryDetector) {
        this.idleEntryDetector = idleEntryDetector;
        return this;
    }

    public long getDefaultMaxIdleTimeInMillis() {
        return defaultMaxIdleTimeInMillis;
    }

    public DataStoreConfigurator<K, V> setDefaultMaxIdleTimeInMillis(long defaultMaxIdleTimeInMillis) {
        this.defaultMaxIdleTimeInMillis = defaultMaxIdleTimeInMillis;
        return this;
    }

    public DataStoreEntryUpdater<K, V> getDataStoreEntryUpdater() {
        return dseUpdater;
    }

    public DataStoreConfigurator<K, V> setDataStoreEntryUpdater(DataStoreEntryUpdater<K, V> dseUpdater) {
        this.dseUpdater = dseUpdater;
        return this;
    }


    public boolean isSafeToDelayCaptureState() {
        return safeToDelayCaptureState;
    }

    public DataStoreConfigurator<K, V> setSafeToDelayCaptureState(boolean safeToDelayCaptureState) {
        this.safeToDelayCaptureState = safeToDelayCaptureState;
        return this;
    }

    public boolean isUseMapToCacheCommands() {
        return useMapToCacheCommands;
    }

    public DataStoreConfigurator<K, V> setUseMapToCacheCommands(boolean useMapToCacheCommands) {
        this.useMapToCacheCommands = useMapToCacheCommands;
        return this;
    }

    @Override
    public String toString() {
        return "DataStoreConfigurator{" +
                "instanceName='" + instanceName + '\'' +
                ", groupName='" + groupName + '\'' +
                ", storeName='" + storeName + '\'' +
                ", keyClazz=" + keyClazz +
                ", valueClazz=" + valueClazz +
                ", keyMapper=" + keyMapper +
                ", startGMS=" + startGMS +
                ", cacheLocally= " + cacheLocally +
                ", clazzLoader=" + clazzLoader +
                ", doSynchronousReplication=" + doSynchronousReplication +
                '}';
    }
}
