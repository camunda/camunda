package org.camunda.tngp.broker.transport.cfg;

import org.camunda.tngp.broker.clustering.worker.cfg.ManagementComponentCfg;

public class TransportComponentCfg
{
    public String hostname = "0.0.0.0";
    public int sendBufferSize = 16;
    public int defaultReceiveBufferSize = 16;

    public SocketBindingCfg clientApi = new SocketBindingCfg();

    public ManagementComponentCfg management = new ManagementComponentCfg();

}
