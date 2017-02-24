package org.camunda.tngp.broker.clustering.gossip.data;

public interface PeerListListener
{
    void onPeerJoin(Peer peer);
}
