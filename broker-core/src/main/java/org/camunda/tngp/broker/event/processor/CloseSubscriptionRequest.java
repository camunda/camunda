package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;

public class CloseSubscriptionRequest extends UnpackedObject
{

    protected IntegerProperty topicIdProp = new IntegerProperty("topicId");
    protected LongProperty subscriptionIdProp = new LongProperty("subscriptionId");

    public CloseSubscriptionRequest()
    {
        this.declareProperty(subscriptionIdProp)
            .declareProperty(topicIdProp);
    }

    public long getSubscriptionId()
    {
        return subscriptionIdProp.getValue();
    }

    public CloseSubscriptionRequest subscriptionId(long subscriptionId)
    {
        this.subscriptionIdProp.setValue(subscriptionId);
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
