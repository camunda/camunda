package org.camunda.tngp.broker.clustering.gossip.data;

public interface PeerSelector
{
    boolean next(Peer dst, Peer[] exclusions);

    int next(int max, Peer[] dst, Peer[] exclusions);
}
