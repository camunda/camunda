package org.camunda.tngp.broker.transport.cfg;

public class TransportComponentCfg
{
    public String hostname = "0.0.0.0";
    public int sendBufferSize = 16;
    public int defaultReceiveBufferSize = 16;

    public SocketBindingCfg clientApi = new SocketBindingCfg();
}
