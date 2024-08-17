package org.apache.rocketmq.example.simple;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;

/**
 * @author qiang.lin
 * @since 2024/5/22
 */
public class PushConsumer2 {
    public static void main(String[] args) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer();
        consumer.setNamesrvAddr("10.13.68.51:9876");
        consumer.setConsumerGroup("linq-aliyun-test-order-consumer");
        consumer.subscribe("tp_multialive_test", "*");
        consumer.setInstanceName("split2_");
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeMessageBatchMaxSize(1);
//        EveryBrokerAllocateQueueStrategy3 everyBrokerAllocateQueueStrategy3 = new EveryBrokerAllocateQueueStrategy3();
//        everyBrokerAllocateQueueStrategy3.setMqConsumerInner(consumer.getDefaultMQPushConsumerImpl());
//        everyBrokerAllocateQueueStrategy3.setOriginAllocateMessageQueueStrategy(new AllocateMessageQueueAveragely());
//        consumer.setAllocateMessageQueueStrategy(everyBrokerAllocateQueueStrategy3);

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {

                System.out.println(Producer2.timeMillisToHumanString(System.currentTimeMillis()) + "|" + new String(msgs.get(0).getBody()) + " " + context.getMessageQueue().getBrokerName());
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer.start();
    }

}
