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

package org.shoal.ha.cache.impl.command;

import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;
import org.shoal.ha.cache.impl.util.ReplicationInputStream;
import org.shoal.ha.cache.impl.util.ReplicationOutputStream;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class StaleCopyRemoveCommand<K, V>
    extends Command<K, V> {

    protected static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_TOUCH_COMMAND);

    private K key;

    private String staleTargetName;

    public StaleCopyRemoveCommand() {
        super(ReplicationCommandOpcode.STALE_REMOVE);
    }

    public K getKey() {
        return key;
    }

    public void setKey(K key) {
        this.key = key;
    }

    public String getStaleTargetName() {
        return staleTargetName;
    }

    public void setStaleTargetName(String targetName) {
        this.staleTargetName = targetName;
    }

    @Override
    protected StaleCopyRemoveCommand<K, V> createNewInstance() {
        return new StaleCopyRemoveCommand<K, V>();
    }

    @Override
    public void writeCommandPayload(ReplicationOutputStream ros)
        throws IOException {
        setTargetName(staleTargetName);
        dsc.getDataStoreKeyHelper().writeKey(ros, getKey());
        if (_logger.isLoggable(Level.INFO)) {
            _logger.log(Level.INFO, dsc.getInstanceName() + " sending stale_copy_remove " + getKey() + " to " + staleTargetName);
        }
    }
    @Override
    public void readCommandPayload(ReplicationInputStream ris)
        throws IOException {
        key = dsc.getDataStoreKeyHelper().readKey(ris);
    }

    @Override
    public void execute(String initiator) {
        if (_logger.isLoggable(Level.INFO)) {
            _logger.log(Level.INFO, dsc.getInstanceName() + " received remove " + key + " from " + initiator);
        }
        dsc.getReplicaStore().remove(key);
    }

}