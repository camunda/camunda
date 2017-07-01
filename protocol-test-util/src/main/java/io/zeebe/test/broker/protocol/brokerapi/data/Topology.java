package io.zeebe.test.broker.protocol.brokerapi.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class Topology
{

    protected List<TopicLeader> topicLeaders = new ArrayList<>();

    public Topology addTopic(final TopicLeader topicLeader)
    {
        topicLeaders.add(topicLeader);
        return this;
    }

    public List<TopicLeader> getTopicLeaders()
    {
        return topicLeaders;
    }

    public Set<BrokerAddress> getBrokers()
    {
        return topicLeaders.stream()
            .map(topicLeader -> new BrokerAddress(topicLeader.getHost(), topicLeader.getPort()))
            .collect(Collectors.toSet());
    }

}
