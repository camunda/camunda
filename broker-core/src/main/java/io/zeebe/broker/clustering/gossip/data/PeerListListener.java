package io.zeebe.broker.clustering.gossip.data;

public interface PeerListListener
{
    void onPeerJoin(Peer peer);
}
