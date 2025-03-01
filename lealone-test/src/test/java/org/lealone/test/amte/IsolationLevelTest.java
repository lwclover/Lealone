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
package org.lealone.test.amte;

import org.junit.Test;
import org.lealone.storage.Storage;
import org.lealone.test.TestBase;
import org.lealone.transaction.Transaction;
import org.lealone.transaction.TransactionEngine;
import org.lealone.transaction.TransactionMap;

public class IsolationLevelTest extends TestBase {

    private TransactionEngine te;
    private Storage storage;
    private final String mapName = IsolationLevelTest.class.getSimpleName();

    @Test
    public void run() {
        te = AMTransactionEngineTest.getTransactionEngine();
        storage = AMTransactionEngineTest.getStorage();
        try {
            test1();
            test2();
        } finally {
            te.close();
        }
    }

    private void test1() {
        Transaction t1 = te.beginTransaction(false);
        TransactionMap<String, String> map = t1.openMap(mapName, storage);
        map.clear();
        map.put("1", "a");
        map.put("2", "b");

        // 只有t2能读到t1未提交的值，其他都读不到
        Transaction t2 = te.beginTransaction(false);
        t2.setIsolationLevel(Transaction.IL_READ_UNCOMMITTED);
        map = map.getInstance(t2);
        assertEquals("a", map.get("1"));

        Transaction t3 = te.beginTransaction(false);
        t3.setIsolationLevel(Transaction.IL_READ_COMMITTED);
        map = map.getInstance(t3);
        assertNull(map.get("1"));

        Transaction t4 = te.beginTransaction(false);
        t4.setIsolationLevel(Transaction.IL_REPEATABLE_READ);
        map = map.getInstance(t4);
        assertNull(map.get("1"));

        Transaction t5 = te.beginTransaction(false);
        t5.setIsolationLevel(Transaction.IL_SERIALIZABLE);
        map = map.getInstance(t5);
        assertNull(map.get("1"));
    }

    private void test2() {
        Transaction t1 = te.beginTransaction(false);
        t1.setIsolationLevel(Transaction.IL_READ_COMMITTED);
        TransactionMap<String, String> map1 = t1.openMap(mapName, storage);
        map1.clear();
        assertNull(map1.get("1"));
        map1.put("2", "b-old");
        map1.put("3", "c");
        t1.commit();

        Transaction t2 = te.beginTransaction(false);
        t2.setIsolationLevel(Transaction.IL_REPEATABLE_READ);
        TransactionMap<String, String> map2 = map1.getInstance(t2);
        assertNull(map2.get("1"));
        assertEquals("c", map2.get("3"));

        Transaction t3 = te.beginTransaction(false);
        t3.setIsolationLevel(Transaction.IL_SERIALIZABLE);
        TransactionMap<String, String> map3 = map1.getInstance(t3);
        assertNull(map3.get("1"));
        assertEquals("c", map3.get("3"));

        Transaction t4 = te.beginTransaction(false);
        TransactionMap<String, String> map4 = map1.getInstance(t4);
        map4.put("1", "a");
        map4.put("2", "b-new");
        map4.remove("3");
        t4.commit();

        // t1能读到t4已经提交的值
        assertEquals("a", map1.get("1"));
        // t2和t3都读不到t4已经提交的值
        assertNull(map2.get("1"));
        assertNull(map3.get("1"));

        // t1能读到t4已经提交的值
        assertEquals("b-new", map1.get("2"));
        // t2和t3都只能读到t1提交的值
        assertEquals("b-old", map2.get("2"));
        assertEquals("b-old", map3.get("2"));

        // t1能看到t3把值删了
        assertNull(map1.get("3"));
        // t2和t3还能看到旧值
        assertEquals("c", map2.get("3"));
        assertEquals("c", map3.get("3"));
    }
}
