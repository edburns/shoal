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

package org.shoal.adapter.store.commands;

import org.shoal.ha.cache.api.DataStoreEntry;
import org.shoal.ha.cache.api.DataStoreException;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.command.Command;
import org.shoal.ha.cache.impl.command.ReplicationCommandOpcode;
import org.shoal.ha.cache.impl.util.ReplicationInputStream;
import org.shoal.ha.cache.impl.util.ReplicationOutputStream;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class SaveCommand<K, V>
    extends AcknowledgedCommand<K, V> {

    private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_SAVE_COMMAND);

    private K k;

    private V v;

    private transient byte[] rawReadState;

    public SaveCommand() {
        super(ReplicationCommandOpcode.SAVE);
    }

    public SaveCommand(K k, V v) {
        this();
        setKey(k);
        setValue(v);
    }

    public void setKey(K k) {
        this.k = k;
    }

    public void setValue(V v) {
        this.v = v;
    }

    @Override
    protected SaveCommand<K, V> createNewInstance() {
        return new SaveCommand<K, V>();
    }

    @Override
    protected void writeCommandPayload(ReplicationOutputStream ros)
        throws IOException {

        if (! dsc.isDoASyncReplication()) {
            super.writeAcknowledgementId(ros);
        }
        dsc.getDataStoreKeyHelper().writeKey(ros, k);
        dsc.getDataStoreEntryHelper().writeObject(ros, v);
    }

    @Override
    public void computeTarget() {
        super.selectReplicaInstance( k);
    }

    @Override
    public void readCommandPayload(ReplicationInputStream ris)
        throws IOException {
        if (! dsc.isDoASyncReplication()) {
            super.readAcknowledgementId(ris);
        }
        k = dsc.getDataStoreKeyHelper().readKey(ris);
        v = (V) dsc.getDataStoreEntryHelper().readObject(ris);
    }

    @Override
    public void execute(String initiator)
        throws DataStoreException {

        DataStoreEntry<K, V> entry = dsc.getReplicaStore().getOrCreateEntry(k);
        synchronized (entry) {
            entry.setV((V) v);
        }

        if (! dsc.isDoASyncReplication()) {
            super.sendAcknowledgement();
        }
    }

    public String toString() {
        return getName() + "(" + k + ")";
    }


    @Override
    public void onSuccess() {
        if (! dsc.isDoASyncReplication()) {
            try {
                super.onSuccess();
                super.waitForAck();
            } catch (Exception ex) {
                System.out.println("** Got exception: " + ex);
            }
        }
    }
}