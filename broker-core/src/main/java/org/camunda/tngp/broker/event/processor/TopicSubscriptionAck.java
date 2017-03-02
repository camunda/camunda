package org.camunda.tngp.broker.event.processor;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class TopicSubscriptionAck extends UnpackedObject
{
    protected StringProperty subscriptionNameProp = new StringProperty("subscriptionName");
    protected LongProperty ackPositionProp = new LongProperty("ackPosition");

    public TopicSubscriptionAck()
    {
        declareProperty(subscriptionNameProp)
            .declareProperty(ackPositionProp);
    }

    public TopicSubscriptionAck subscriptionName(DirectBuffer nameBuffer, int offset, int length)
    {
        this.subscriptionNameProp.setValue(nameBuffer, offset, length);
        return this;
    }

    public DirectBuffer getSubscriptionName()
    {
        return subscriptionNameProp.getValue();
    }

    public long getAckPosition()
    {
        return ackPositionProp.getValue();
    }

    public void ackPosition(long ackPosition)
    {
        this.ackPositionProp.setValue(ackPosition);
    }

}
