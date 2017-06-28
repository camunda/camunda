package org.camunda.tngp.broker.clustering.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;


public class BrokerAddress extends UnpackedObject
{
    protected StringProperty hostProp = new StringProperty("host");
    protected IntegerProperty portProp = new IntegerProperty("port");

    public BrokerAddress()
    {
        this
            .declareProperty(hostProp)
            .declareProperty(portProp);
    }

    public DirectBuffer getHost()
    {
        return hostProp.getValue();
    }

    public BrokerAddress setHost(final DirectBuffer host, final int offset, final int length)
    {
        this.hostProp.setValue(host, offset, length);
        return this;
    }

    public int getPort()
    {
        return portProp.getValue();
    }

    public BrokerAddress setPort(final int port)
    {
        portProp.setValue(port);
        return this;
    }

}
