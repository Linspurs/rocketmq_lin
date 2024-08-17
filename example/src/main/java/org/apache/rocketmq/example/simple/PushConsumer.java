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
package org.apache.rocketmq.example.simple;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;

public class PushConsumer {

    public static void main(String[] args) throws InterruptedException, MQClientException {
        AclClientRPCHook aclClientRPCHook = new AclClientRPCHook(new SessionCredentials("lLYzee98UN5v0cnS", "C38pNX1CPh9vCVYt"));
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(aclClientRPCHook);
        consumer.setConsumerGroup("linq-aliyun-test-order-consumer");
        consumer.subscribe("tp_linq_aliyun_test", "*");
        consumer.setInstanceName("aliyun");
        consumer.setVipChannelEnabled(false);

        consumer.setNamesrvAddr("rmq-cn-o493n57d205-vpc.cn-hangzhou.rmq.aliyuncs.com:8080");
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(20);
        consumer.setConsumeMessageBatchMaxSize(32);
//        consumer.registerMessageListener(new MessageListenerOrderly() {
//
//            @Override
//            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
//                System.out.printf("%s Receive New Queue: %s Messages: %s %n", Thread.currentThread().getName(), context.getMessageQueue(), new String(msgs.get(0).getBody()));
//                return ConsumeOrderlyStatus.SUCCESS;
//            }
//        });

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
//                try{Thread.sleep(500);}catch (Exception e){}

//                System.out.printf("%s Receive New Queue: %s Messages: %s %n", Thread.currentThread().getName(), context.getMessageQueue(), new String(msgs.get(0).getBody()));
                System.out.printf("%s Receive New Queue: %s Messages: %s %n", Thread.currentThread().getName(), timeMillisToHumanString(System.currentTimeMillis()), new String(msgs.get(0).getBody()));
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
        System.out.printf("Consumer Started. %s%n", timeMillisToHumanString(System.currentTimeMillis()));

        AclClientRPCHook aclClientRPCHook1 = new AclClientRPCHook(new SessionCredentials("lLYzee98UN5v0cnS", "C38pNX1CPh9vCVYt"));
        DefaultMQPushConsumer consumer1 = new DefaultMQPushConsumer(aclClientRPCHook1);
        consumer1.setConsumerGroup("linq-aliyun-test-order-consumer");
        consumer1.subscribe("tp_linq_aliyun_test", "*");
        consumer1.setInstanceName("aliyun1");
        consumer1.setVipChannelEnabled(false);

        consumer1.setNamesrvAddr("rmq-cn-o493n57d205-vpc.cn-hangzhou.rmq.aliyuncs.com:8080");
        consumer1.setConsumeThreadMin(1);
        consumer1.setConsumeThreadMax(20);
        consumer1.setConsumeMessageBatchMaxSize(32);
//        consumer.registerMessageListener(new MessageListenerOrderly() {
//
//            @Override
//            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
//                System.out.printf("%s Receive New Queue: %s Messages: %s %n", Thread.currentThread().getName(), context.getMessageQueue(), new String(msgs.get(0).getBody()));
//                return ConsumeOrderlyStatus.SUCCESS;
//            }
//        });

        consumer1.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
//                try{Thread.sleep(500);}catch (Exception e){}

//                System.out.printf("%s Receive New Queue: %s Messages: %s %n", Thread.currentThread().getName(), context.getMessageQueue(), new String(msgs.get(0).getBody()));
                System.out.printf("1 %s Receive New Queue: %s Messages: %s %n", Thread.currentThread().getName(), timeMillisToHumanString(System.currentTimeMillis()), new String(msgs.get(0).getBody()));
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer1.start();
        System.out.printf("Consumer Started. %s%n", timeMillisToHumanString(System.currentTimeMillis()));
    }

    public static String timeMillisToHumanString(Long time) {
        if (time == null || time <= 0) {
            return "1970-01-01 08:00:00";
        }

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTimeFormatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()));
    }
}
