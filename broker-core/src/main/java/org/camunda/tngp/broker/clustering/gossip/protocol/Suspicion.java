package org.camunda.tngp.broker.clustering.gossip.protocol;

import static org.camunda.tngp.clustering.gossip.PeerState.*;

import java.util.concurrent.TimeUnit;

import org.camunda.tngp.broker.clustering.gossip.GossipContext;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerListIterator;

public class Suspicion
{
    private int timeout;
    private final PeerList peers;
    private final PeerListIterator iterator;

    public Suspicion(final GossipContext context)
    {
        final GossipConfiguration config = context.getConfig();
        this.timeout = config.suspicionTimeout;
        this.peers = context.getPeers();
        this.iterator = new PeerListIterator(peers);
    }

    public void open()
    {
    }

    public void close()
    {
    }

    public int doWork()
    {
        int workcount = 0;

        iterator.reset();
        while (iterator.hasNext())
        {
            final Peer peer = iterator.next();
            if (peer.state() == SUSPECT)
            {
                final long suspectTime = peer.changeStateTime();
                if (System.currentTimeMillis() > suspectTime + TimeUnit.SECONDS.toMillis(timeout))
                {
                    workcount += 1;
                    peer.dead();
                    peers.set(iterator.position(), peer);
                }
            }
        }

        return workcount;
    }

}
