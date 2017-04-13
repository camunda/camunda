package org.camunda.tngp.broker.transport.cfg;

import org.camunda.tngp.broker.system.ComponentConfiguration;

public class SocketBindingCfg extends ComponentConfiguration
{
    public String host;
    public int port = -1;
    public int receiveBufferSize = -1;
    public long controlMessageRequestTimeoutInMillis = 10_000;
}
