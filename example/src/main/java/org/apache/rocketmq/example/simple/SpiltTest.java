package org.apache.rocketmq.example.simple;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author qiang.lin
 * @since 2024/3/28
 */
public class SpiltTest {
    public static void main(String[] args) {
        DefaultMQProducer producer = new DefaultMQProducer("ProducerGroupName");
        producer.setInstanceName("onlinemq1");
        producer.setNamesrvAddr("10.13.66.141:9876");
        try {
            producer.start();
        } catch (MQClientException e) {
            e.printStackTrace();
        }

        while (true) {
            Message message = new Message("tp_multialive_test", "hello".getBytes(StandardCharsets.UTF_8));
            try {
                SendResult send = producer.send(message);
                DateFormat df1 = new SimpleDateFormat("yyyy年MM月dd日HH:mm:ss");
                Date date = new Date();
                String s = df1.format(date);
                System.out.println(s + " " + send.getMessageQueue().getBrokerName() + " " + send.getMessageQueue().getQueueId());
                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
