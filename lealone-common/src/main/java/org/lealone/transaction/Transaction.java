/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.transaction;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.lealone.common.exceptions.DbException;
import org.lealone.db.Session;
import org.lealone.storage.Storage;
import org.lealone.storage.type.StorageDataType;

public interface Transaction {

    int IL_READ_UNCOMMITTED = Connection.TRANSACTION_READ_UNCOMMITTED;

    int IL_READ_COMMITTED = Connection.TRANSACTION_READ_COMMITTED;

    int IL_REPEATABLE_READ = Connection.TRANSACTION_REPEATABLE_READ;

    int IL_SERIALIZABLE = Connection.TRANSACTION_SERIALIZABLE;

    /**
     * The status of a closed transaction (committed or rolled back).
     */
    public static final int STATUS_CLOSED = 0;

    /**
     * The status of an open transaction.
     */
    public static final int STATUS_OPEN = 1;

    /**
     * The status of a transaction that is being committed, but possibly not
     * yet finished. A transaction can go into this state when the store is
     * closed while the transaction is committing. When opening a store,
     * such transactions should be committed.
     */
    public static final int STATUS_COMMITTING = 3;

    int getStatus();

    void setStatus(int status);

    void setIsolationLevel(int level);

    int getIsolationLevel();

    long getTransactionId();

    boolean isAutoCommit();

    void setAutoCommit(boolean autoCommit);

    void setLocal(boolean local);

    void addLocalTransactionNames(String localTransactionNames);

    String getLocalTransactionNames();

    String getGlobalTransactionName();

    void setGlobalTransactionName(String globalTransactionName);

    void setValidator(Validator validator);

    void setSession(Session session);

    void addParticipant(Participant participant);

    /**
     * Open a data map.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param name the name of the map
     * @return the transaction map
     */
    <K, V> TransactionMap<K, V> openMap(String name, Storage storage);

    /**
     * Open the map to store the data.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param name the name of the map
     * @param keyType the key data type
     * @param valueType the value data type
     * @return the transaction map
     */
    <K, V> TransactionMap<K, V> openMap(String name, StorageDataType keyType, StorageDataType valueType,
            Storage storage);

    <K, V> TransactionMap<K, V> openMap(String name, StorageDataType keyType, StorageDataType valueType,
            Storage storage, Map<String, String> parameters);

    void addSavepoint(String name);

    int getSavepointId();

    void prepareCommit();

    void prepareCommit(String allLocalTransactionNames);

    void commit();

    void commit(String allLocalTransactionNames);

    void rollback();

    void rollbackToSavepoint(String name);

    void rollbackToSavepoint(int savepointId);

    void replicationPrepareCommit(long validKey);

    interface Participant {
        void addSavepoint(String name);

        void rollbackToSavepoint(String name);

        void commitTransaction(String localTransactionName);

        void rollbackTransaction();
    }

    interface Validator {
        boolean validate(String localTransactionName);

        boolean validate(String hostAndPort, String localTransactionName);
    }

    interface Listener {
        default void beforeOperation() {
        }

        void operationUndo();

        void operationComplete();

        default void setException(RuntimeException e) {
        }

        default RuntimeException getException() {
            return null;
        }

        default void await() {
        }
    }

    class SyncListener implements Listener {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile RuntimeException e;

        @Override
        public void operationUndo() {
            latch.countDown();
        }

        @Override
        public void operationComplete() {
            latch.countDown();
        }

        @Override
        public void setException(RuntimeException e) {
            this.e = e;
        }

        @Override
        public RuntimeException getException() {
            return e;
        }

        @Override
        public void await() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                setException(DbException.convert(e));
            }
            if (e != null)
                throw e;
        }
    }
}
