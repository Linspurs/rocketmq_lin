package org.apache.rocketmq.tools;

import com.google.common.collect.Sets;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.protocol.body.TopicList;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExtImpl;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author qiang.lin
 * @since 2024/3/27
 */
public class AdminTest {
    public static void main(String[] args) {
        DefaultMQAdminExt defaultMQAdminExt = new DefaultMQAdminExt();
        defaultMQAdminExt.setAdminExtGroup("defaultMQAdminExt");
        defaultMQAdminExt.setInstanceName("rmq1");
        // 10.13.68.50:9876;10.13.68.51:9876
        // 10.13.66.140:9876;10.13.66.141:9876
        defaultMQAdminExt.setNamesrvAddr("10.13.68.50:9876");

        try {
            defaultMQAdminExt.start();
            defaultMQAdminExt.examineConsumerConnectionInfo("linq-aliyun-test-order-consumer");
//            TopicConfig topicConfig = new TopicConfig("tp_multialive_test");

//            defaultMQAdminExt.createAndUpdateTopicConfig("10.13.66.143:10911", topicConfig);
//            defaultMQAdminExt.deleteTopicInBroker(Sets.newHashSet("10.13.68.186:10911", "10.13.66.143:10911"), "tp_multialive_test");
//            defaultMQAdminExt.deleteTopicInNameServer(Sets.newHashSet("10.13.66.140:9876", "10.13.66.141:9876"), "tp_multialive_test");

//            TopicList topicList = defaultMQAdminExt.fetchAllTopicList();
//            System.out.println(topicList.getTopicList().contains("tp_multialive_test_split"));
//            TopicRouteData tp_multialive_test = defaultMQAdminExt.examineTopicRouteInfo("tp_multialive_test_split");
//            System.out.println(tp_multialive_test);
//
//            defaultMQAdminExt.deleteTopicInBroker(Sets.newHashSet("10.13.68.52:10911","10.13.67.226:10911"), "tp_multialive_test_split");
//            TopicRouteData tp_multialive_test1 = defaultMQAdminExt.examineTopicRouteInfo("tp_multialive_test_split");
//            System.out.println(tp_multialive_test1);

            TopicList split = defaultMQAdminExt.fetchTopicsByCLuster("split_2");
//            TopicList qaCommon = defaultMQAdminExt.fetchTopicsByCLuster("qa-binlog");

//            List<String> collect = qa.getTopicList().stream().filter(e -> !qaCommon.getTopicList().contains(e)).collect(Collectors.toList());

            for (String topic : split.getTopicList()) {
                if (topic.startsWith("tp_") || topic.startsWith("grey_") || topic.startsWith("%RETRY") || topic.startsWith("%DLQ") || topic.startsWith("bc")) {
                    defaultMQAdminExt.deleteTopicInBroker(Sets.newHashSet("10.13.67.226:10911"), topic);
                    defaultMQAdminExt.deleteTopicInNameServer(Sets.newHashSet("10.13.68.51:9876"), topic);
                    System.out.println(topic);
                }
////                defaultMQAdminExt.createAndUpdateTopicConfig("10.100.5.222:10911", topicConfig);
//
            }

//            defaultMQAdminExt.deleteTopicInNameServer(Sets.newHashSet("10.13.66.141:9876", "10.13.66.140:9876"), "tp_multialive_test");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            defaultMQAdminExt.shutdown();
        }
    }
}
