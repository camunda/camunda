package org.camunda.tngp.test.broker.protocol.brokerapi.data;

public class BrokerAddress
{

    protected final String host;
    protected final int port;

    public BrokerAddress(final String host, final int port)
    {
        this.host = host;
        this.port = port;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

}
