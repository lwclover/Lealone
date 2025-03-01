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
package org.lealone.p2p.net;

import java.util.EnumSet;

import org.lealone.common.logging.Logger;
import org.lealone.common.logging.LoggerFactory;
import org.lealone.db.async.AsyncTask;
import org.lealone.p2p.gms.Gossiper;

@SuppressWarnings({ "rawtypes", "unchecked" })
class MessageDeliveryTask implements AsyncTask {
    private static final Logger logger = LoggerFactory.getLogger(MessageDeliveryTask.class);

    private static final EnumSet<Verb> GOSSIP_VERBS = EnumSet.of(Verb.GOSSIP_DIGEST_ACK, Verb.GOSSIP_DIGEST_ACK2,
            Verb.GOSSIP_DIGEST_SYN);

    private final MessageIn message;
    private final long constructionTime;
    private final int id;

    public MessageDeliveryTask(MessageIn message, int id, long timestamp) {
        assert message != null;
        this.message = message;
        this.id = id;
        constructionTime = timestamp;
    }

    @Override
    public int getPriority() {
        return MIN_PRIORITY; // 集群之间的消息处理不急迫，所以优先级最低
    }

    @Override
    public void run() {
        Verb verb = message.verb;
        if (MessagingService.DROPPABLE_VERBS.contains(verb)
                && System.currentTimeMillis() > constructionTime + message.getTimeout()) {
            MessagingService.instance().incrementDroppedMessages(verb);
            return;
        }

        IVerbHandler verbHandler = verb.verbHandler;
        if (verbHandler == null) {
            if (logger.isDebugEnabled())
                logger.debug("Unknown verb {}", verb);
            return;
        }

        try {
            verbHandler.doVerb(message, id);
        } catch (Throwable t) {
            if (message.doCallbackOnFailure()) {
                MessageOut response = new MessageOut(Verb.INTERNAL_RESPONSE)
                        .withParameter(MessagingService.FAILURE_RESPONSE_PARAM, MessagingService.ONE_BYTE);
                MessagingService.instance().sendReply(response, id, message.from);
            }
            throw t;
        }
        if (GOSSIP_VERBS.contains(message.verb))
            Gossiper.instance.setLastProcessedMessageAt(constructionTime);
    }
}
