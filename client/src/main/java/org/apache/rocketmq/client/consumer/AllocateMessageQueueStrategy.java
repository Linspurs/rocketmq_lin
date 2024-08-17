/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.client.consumer;

import java.util.List;
import org.apache.rocketmq.common.message.MessageQueue;

/**
 * K1 分配算法 队列q1 q2 q3 q4 q5 q6 q7 q8| 消费者 c1 c2 c3<p>
 *  1、平均:
 *      c1-- q1 q2 q3
 *      c2-- q4 q5 q6
 *      c3-- q7 q8
 *  2、平均轮询:
 *      c1-- q1 q4 q7
 *      c2-- q2 q5 q8
 *      c3-- q3 q6
 *  3、一致性hash
 *  4、根据配置
 *  5、根据broker部署机房
 *  注意：
 *      一个consumer可分配多个queue，一个queue只能有一个consumer，
 *      如果consumer个数大于queue个数，则有些consumer将消费不到消息
 *  Strategy Algorithm for message allocating between consumers
 */
public interface AllocateMessageQueueStrategy {

    /**
     * Allocating by consumer id
     *
     * @param consumerGroup current consumer group
     * @param currentCID current consumer id
     * @param mqAll message queue set in current topic
     * @param cidAll consumer set in current consumer group
     * @return The allocate result of given strategy
     */
    List<MessageQueue> allocate(
        final String consumerGroup,
        final String currentCID,
        final List<MessageQueue> mqAll,
        final List<String> cidAll
    );

    /**
     * Algorithm name
     *
     * @return The strategy name
     */
    String getName();
}
