/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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

package org.shoal.ha.cache.impl.store;

import org.shoal.ha.cache.api.DataStoreEntry;
import org.shoal.ha.cache.api.DataStoreEntryEvaluator;
import org.shoal.ha.cache.api.DataStoreEntryHelper;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.impl.util.ObjectInputStreamWithLoader;
import org.shoal.ha.cache.impl.util.ReplicationOutputStream;
import org.shoal.ha.cache.impl.util.ReplicationState;
import org.shoal.ha.cache.impl.util.SimpleSerializer;

import java.io.*;

/**
 * @author Mahesh Kannan
 */
public class DefaultDataStoreEntryHelper<K, V>
        implements DataStoreEntryHelper<K, V> {

    private ClassLoader loader;

    public DefaultDataStoreEntryHelper(ClassLoader cl) {
        this.loader = cl;
    }

    @Override
    public DataStoreEntry<K, V> createDataStoreEntry() {
        return new ReplicationState<K, V>();
    }

    @Override
    public DataStoreEntry<K, V> createDataStoreEntry(K k, V obj) {
        ReplicationState<K, V> state = new ReplicationState<K, V>();
        state.setKey(k);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.flush();
        } catch (IOException ioEx) {
            //TODO
        } finally {
            try {
                oos.close();
            } catch (IOException ioEx) {
            }
            try {
                bos.close();
            } catch (IOException ioEx) {
            }
        }

        byte[] data = bos.toByteArray();
        state.setAttribute("value", data);

        return state;
    }

    @Override
    public V getV(DataStoreEntry<K, V> replicationEntry)
            throws DataStoreException {
        byte[] data = ((ReplicationState<K, V>) replicationEntry).getAttribute("value");
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStreamWithLoader ois = null;
        V v = null;
        try {
            ois = new ObjectInputStreamWithLoader(bis, loader);
            v = (V) ois.readObject();
        } catch (IOException ioEx) {
            throw new DataStoreException(ioEx);
        } catch (ClassNotFoundException cnfEx) {
            throw new DataStoreException(cnfEx);
        } finally {
            try {ois.close();} catch (IOException ioEx1) {}
            try {bis.close();} catch (IOException ioEx2) {}
        }

        return v;
    }

    @Override
    public void writeObject(ReplicationOutputStream ros, Object obj)
            throws IOException {
        SimpleSerializer.serialize(ros, obj);
    }

    @Override
    public Object readObject(byte[] data, int index) throws DataStoreException {
        try {
            return SimpleSerializer.deserialize(loader, data, index);
        } catch (ClassNotFoundException cnfEx) {
            throw new DataStoreException("Cannot desrialize value", cnfEx);
        } catch (IOException ioEx) {
            throw new DataStoreException("Cannot desrialize value", ioEx);
        }
    }

    @Override
    public void updateDelta(K k, DataStoreEntry<K, V> kvDataStoreEntry, Object obj) {
        throw new UnsupportedOperationException("updateDelta(K k, DataStoreEntry<K, V> kvDataStoreEntry, Object obj) not supported");
    }

}