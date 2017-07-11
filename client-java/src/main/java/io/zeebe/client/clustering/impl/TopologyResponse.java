package io.zeebe.client.clustering.impl;

import java.util.List;

import io.zeebe.transport.SocketAddress;

public class TopologyResponse
{
    private List<SocketAddress> brokers;

    private List<TopicLeader> topicLeaders;

    public List<SocketAddress> getBrokers()
    {
        return brokers;
    }

    public void setBrokers(List<SocketAddress> brokers)
    {
        this.brokers = brokers;
    }

    public List<TopicLeader> getTopicLeaders()
    {
        return topicLeaders;
    }

    public void setTopicLeaders(List<TopicLeader> topicLeaders)
    {
        this.topicLeaders = topicLeaders;
    }
}
