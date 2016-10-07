package org.camunda.tngp.broker.clustering.gossip;

import org.camunda.tngp.broker.clustering.gossip.data.Peer;

public interface GossipListener
{
    void onPeerJoin(Peer peer);
}
