package org.camunda.tngp.broker.clustering.gossip.protocol;

import static org.camunda.tngp.management.gossip.PeerState.SUSPECT;

import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerListIterator;

public class Suspicion
{
    protected final EpochClock clock = new SystemEpochClock();
    protected final int suspicionTimeout;
    protected final GossipProtocol gossipProtocol;

    public Suspicion(final GossipProtocol gossipProtocol, final int suspicionTimeout)
    {
        this.gossipProtocol = gossipProtocol;
        this.suspicionTimeout = suspicionTimeout;
    }

    public int process()
    {
        int workcount = 0;

        final PeerList members = gossipProtocol.getMembers();
        final PeerListIterator iterator = members.iterator();
        while (iterator.hasNext())
        {
            final Peer peer = iterator.next();

            if (peer.state() == SUSPECT)
            {
                final long suspectTime = peer.changeStateTime();
                if (clock.time() > suspectTime + TimeUnit.SECONDS.toMillis(suspicionTimeout))
                {
//                    System.out.println("[SUSPICION] now: " + System.currentTimeMillis() + ", endpoint: " + peer.endpoint());
                    workcount += 1;
                    members.markPeerAsDead(peer);
                }
            }
        }

        return workcount;
    }
}
