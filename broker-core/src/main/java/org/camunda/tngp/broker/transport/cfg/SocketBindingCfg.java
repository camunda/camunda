package org.camunda.tngp.broker.transport.cfg;

public class SocketBindingCfg
{
    public String hostname;
    public int port = -1;
    public int receiveBufferSize = -1;
    public long controlMessageRequestTimeoutInMillis = 30_000;
}
