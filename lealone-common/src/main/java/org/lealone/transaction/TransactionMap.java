/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.lealone.transaction;

import java.util.Iterator;
import java.util.Map.Entry;

import org.lealone.storage.IterationParameters;
import org.lealone.storage.StorageMap;

public interface TransactionMap<K, V> extends StorageMap<K, V> {

    /**
     * Get the size of the raw map. This includes uncommitted entries, and
     * transiently removed entries, so it is the maximum number of entries.
     *
     * @return the maximum size
     */
    public long rawSize();

    /**
     * Get a clone of this map for the given transaction.
     *
     * @param transaction the transaction
     * @return the map
     */
    public TransactionMap<K, V> getInstance(Transaction transaction);

    /**
     * Update the value for the given key, without adding an undo log entry.
     *
     * @param key the key
     * @param value the value
     * @return the old value
     */
    public V putCommitted(K key, V value);

    /**
     * Iterate over entries.
     *
     * @param from the first key to return
     * @return the iterator
     */
    public Iterator<Entry<K, V>> entryIterator(K from);

    public Iterator<Entry<K, V>> entryIterator(IterationParameters<K> parameters);

    /**
     * Iterate over keys.
     *
     * @param from the first key to return
     * @return the iterator
     */
    public Iterator<K> keyIterator(K from);

    /**
     * Iterate over keys.
     *
     * @param from the first key to return
     * @param includeUncommitted whether uncommitted entries should be included
     * @return the iterator
     */
    public Iterator<K> keyIterator(K from, boolean includeUncommitted);

    public void addIfAbsent(K key, V value, Transaction.Listener listener);

    public default boolean tryUpdate(K key, V newValue) {
        Object oldTransactionalValue = getTransactionalValue(key);
        return tryUpdate(key, newValue, null, oldTransactionalValue);
    }

    public default boolean tryUpdate(K key, V newValue, Object oldTransactionalValue) {
        return tryUpdate(key, newValue, null, oldTransactionalValue);
    }

    public default boolean tryUpdate(K key, V newValue, int[] columnIndexes) {
        Object oldTransactionalValue = getTransactionalValue(key);
        return tryUpdate(key, newValue, columnIndexes, oldTransactionalValue);
    }

    public boolean tryUpdate(K key, V newValue, int[] columnIndexes, Object oldTransactionalValue);

    public default boolean tryRemove(K key) {
        Object oldTransactionalValue = getTransactionalValue(key);
        return tryRemove(key, oldTransactionalValue);
    }

    public boolean tryRemove(K key, Object oldTransactionalValue);

    public default boolean tryLock(K key) {
        Object oldTransactionalValue = getTransactionalValue(key);
        return tryLock(key, oldTransactionalValue);
    }

    public boolean tryLock(K key, Object oldTransactionalValue);

    public boolean isLocked(Object oldTransactionalValue, int[] columnIndexes);

    public Object[] getValueAndRef(K key, int[] columnIndexes);

    public Object getTransactionalValue(K key);
}
