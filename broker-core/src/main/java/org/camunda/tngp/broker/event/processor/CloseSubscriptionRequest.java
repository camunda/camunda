package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;

public class CloseSubscriptionRequest extends UnpackedObject
{

    protected LongProperty idProp = new LongProperty("subscriptionId");

    public CloseSubscriptionRequest()
    {
        this.declareProperty(idProp);
    }

    public long getSubscriptionId()
    {
        return idProp.getValue();
    }

    public CloseSubscriptionRequest subscriptionId(long subscriptionId)
    {
        this.idProp.setValue(subscriptionId);
        return this;
    }
}
