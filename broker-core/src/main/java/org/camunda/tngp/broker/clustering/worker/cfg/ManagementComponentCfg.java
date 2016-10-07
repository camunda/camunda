package org.camunda.tngp.broker.clustering.worker.cfg;

import org.camunda.tngp.broker.clustering.gossip.cfg.GossipCfg;

public class ManagementComponentCfg
{
    public String host = "localhost";
    public int port = -1;
    public int receiveBufferSize = -1;
    public GossipCfg gossip = new GossipCfg();
}
