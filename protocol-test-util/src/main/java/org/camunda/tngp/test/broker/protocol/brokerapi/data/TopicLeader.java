package org.camunda.tngp.test.broker.protocol.brokerapi.data;

public class TopicLeader
{

    private final String host;
    private final int port;
    private final String topicName;
    private final int partitionId;

    public TopicLeader(final String host, final int port, final String topicName, final int partitionId)
    {
        this.host = host;
        this.port = port;
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    public String getHost()
    {

        return host;
    }

    public int getPort()
    {
        return port;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

}
