package io.zeebe.client.clustering.impl;

import io.zeebe.client.impl.Topic;
import io.zeebe.transport.SocketAddress;


public class TopicLeader
{
    protected String host;
    protected int port;
    protected String topicName;
    protected int partitionId;

    public TopicLeader setHost(final String host)
    {
        this.host = host;
        return this;
    }

    public TopicLeader setPort(final int port)
    {
        this.port = port;
        return this;
    }

    public TopicLeader setTopicName(final String topicName)
    {
        this.topicName = topicName;
        return this;
    }

    public TopicLeader setPartitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public Topic getTopic()
    {
        return new Topic(topicName, partitionId);
    }


    public SocketAddress getSocketAddress()
    {
        return new SocketAddress(host, port);
    }

}
