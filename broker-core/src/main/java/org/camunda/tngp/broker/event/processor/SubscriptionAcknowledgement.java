package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;

public class SubscriptionAcknowledgement extends UnpackedObject
{

    protected LongProperty idProp = new LongProperty("subscriptionId");
    protected LongProperty acknowledgedPositionProp = new LongProperty("acknowledgedPosition");

    public SubscriptionAcknowledgement()
    {
        this
            .declareProperty(idProp)
            .declareProperty(acknowledgedPositionProp);
    }

    public long getSubscriptionId()
    {
        return idProp.getValue();
    }

    public long getAcknowledgedPosition()
    {
        return acknowledgedPositionProp.getValue();
    }
}
