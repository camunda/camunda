package io.zeebe.broker.event.processor;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

public class CloseSubscriptionRequest extends UnpackedObject
{

    protected StringProperty topicNameProp = new StringProperty("topicName");
    protected IntegerProperty partitionIdProp = new IntegerProperty("partitionId");
    protected LongProperty subscriberKeyProp = new LongProperty("subscriberKey");

    public CloseSubscriptionRequest()
    {
        this.declareProperty(subscriberKeyProp)
            .declareProperty(topicNameProp)
            .declareProperty(partitionIdProp);
    }

    public long getSubscriberKey()
    {
        return subscriberKeyProp.getValue();
    }

    public CloseSubscriptionRequest setSubscriberKey(long subscriberKey)
    {
        this.subscriberKeyProp.setValue(subscriberKey);
        return this;
    }

    public DirectBuffer getTopicName()
    {
        return topicNameProp.getValue();
    }

    public CloseSubscriptionRequest setTopicName(final DirectBuffer topicName)
    {
        this.topicNameProp.setValue(topicName);
        return this;
    }

    public int getPartitionId()
    {
        return partitionIdProp.getValue();
    }

    public CloseSubscriptionRequest setPartitionId(int partitionId)
    {
        this.partitionIdProp.setValue(partitionId);
        return this;
    }
}
