package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;

public class CloseSubscriptionRequest extends UnpackedObject
{

    protected IntegerProperty topicIdProp = new IntegerProperty("topicId");
    protected LongProperty subscriberKeyProp = new LongProperty("subscriberKey");

    public CloseSubscriptionRequest()
    {
        this.declareProperty(subscriberKeyProp)
            .declareProperty(topicIdProp);
    }

    public long getSubscriberKey()
    {
        return subscriberKeyProp.getValue();
    }

    public CloseSubscriptionRequest subscriberKey(long subscriberKey)
    {
        this.subscriberKeyProp.setValue(subscriberKey);
        return this;
    }

    public int getTopicId()
    {
        return topicIdProp.getValue();
    }

    public CloseSubscriptionRequest topicId(int topicId)
    {
        this.topicIdProp.setValue(topicId);
        return this;
    }
}
