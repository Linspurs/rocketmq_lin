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

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Producer {
    public static void main(String[] args) throws MQClientException, InterruptedException {
        // aeJk840JawJC451q  3Q3mcdQXC0JFj12k
        // lLYzee98UN5v0cnS  C38pNX1CPh9vCVYt
        AclClientRPCHook aclClientRPCHook = new AclClientRPCHook(new SessionCredentials("lLYzee98UN5v0cnS","C38pNX1CPh9vCVYt"));
        DefaultMQProducer producer = new DefaultMQProducer("ProducerGroupName", aclClientRPCHook);
        // serverless ep-bp1ib5b6c4da7c0dc1c5.epsrv-bp1g2k84c7kid2igyiun.cn-hangzhou.privatelink.aliyuncs.com:8080
        // 专业版 rmq-cn-o493n57d205-vpc.cn-hangzhou.rmq.aliyuncs.com:8080
        producer.setNamesrvAddr("rmq-cn-o493n57d205-vpc.cn-hangzhou.rmq.aliyuncs.com:8080");
        producer.setInstanceName("aliyun");
        producer.setSendMessageWithVIPChannel(false);
        producer.start();

        ExecutorService executors = Executors.newFixedThreadPool(8);

        while (true) {
            try {
                {
                    Message msg = new Message("tp_linq_aliyun_test", timeMillisToHumanString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
                    msg.putUserProperty("zone","hzack");
//                    executors.submit(() -> {
//                        try {
//                            producer.send(msg);
//                        } catch (MQClientException e) {
//                            e.printStackTrace();
//                        } catch (RemotingException e) {
//                            e.printStackTrace();
//                        } catch (MQBrokerException e) {
//                            e.printStackTrace();
//                        } catch (InterruptedException exception) {
//                            exception.printStackTrace();
//                        }
//                    });
                    SendResult send = producer.send(msg);
                    System.out.println(send.getMessageQueue());

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String timeMillisToHumanString(Long time) {
        if (time == null || time <= 0) {
            return "1970-01-01 08:00:00";
        }

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTimeFormatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()));
    }
}
