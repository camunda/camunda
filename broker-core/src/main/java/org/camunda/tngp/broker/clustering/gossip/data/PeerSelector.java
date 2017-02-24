package org.camunda.tngp.broker.clustering.gossip.data;

public interface PeerSelector
{
    boolean next(final Peer dst, final Peer[] exclusions);

    int next(final int max, final Peer[] dst, final Peer[] exclusions);
}
