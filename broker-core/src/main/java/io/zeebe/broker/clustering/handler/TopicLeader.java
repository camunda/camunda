package io.zeebe.broker.clustering.handler;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;


public class TopicLeader extends UnpackedObject
{
    protected StringProperty hostProp = new StringProperty("host");
    protected IntegerProperty portProp = new IntegerProperty("port");
    protected StringProperty topicNameProp = new StringProperty("topicName");
    protected IntegerProperty partitionIdProp = new IntegerProperty("partitionId");

    public TopicLeader()
    {
        this
            .declareProperty(hostProp)
            .declareProperty(portProp)
            .declareProperty(topicNameProp)
            .declareProperty(partitionIdProp);
    }

    public DirectBuffer getHost()
    {
        return hostProp.getValue();
    }

    public TopicLeader setHost(final DirectBuffer host, final int offset, final int length)
    {
        this.hostProp.setValue(host, offset, length);
        return this;
    }

    public int getPort()
    {
        return portProp.getValue();
    }

    public TopicLeader setPort(final int port)
    {
        portProp.setValue(port);
        return this;
    }

    public DirectBuffer getTopicNameProp()
    {
        return topicNameProp.getValue();
    }

    public TopicLeader setTopicName(final DirectBuffer topicName, final int offset, final int length)
    {
        this.topicNameProp.setValue(topicName, offset, length);
        return this;
    }

    public int getPartitionId()
    {
        return partitionIdProp.getValue();
    }

    public TopicLeader setPartitionId(final int partitionId)
    {
        partitionIdProp.setValue(partitionId);
        return this;
    }
}
