package org.camunda.tngp.broker.clustering.gossip.data;

import static org.camunda.tngp.management.gossip.PeerState.ALIVE;

import java.util.Random;

import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;

public class ShuffledPeerList
{
    protected final Random random = new Random();
    protected final GossipProtocol gossipProtocol;
    protected final PeerList shuffledMembers;
    protected int position = -1;

    protected final Peer current = new Peer();

    public ShuffledPeerList(GossipProtocol gossipProtocol)
    {
        this.gossipProtocol = gossipProtocol;

        final PeerList members = gossipProtocol.getMembers();
        this.shuffledMembers = new PeerList(members.capacity());
    }

    protected void shuffle()
    {
        shuffledMembers.clear();

        final PeerList members = gossipProtocol.getMembers();
        shuffledMembers.addAll(members);
        shuffledMembers.shuffle();

        position = -1;
    }

    public void next(final Peer dst)
    {
        if (position < 0 || position + 1 == shuffledMembers.size())
        {
            shuffle();
        }

        position++;
        shuffledMembers.get(position, dst);
    }

    public int next(final int max, final Peer[] dst, final Peer exclude)
    {

        final int n = shuffledMembers.size();
        int dstIdx = 0;

        for (int i = 0; i < n * 3 && dstIdx < max; i++)
        {
            final int idx = random.nextInt(n);
            shuffledMembers.get(idx, current);

            if (current.localPeer() || current.state() != ALIVE)
            {
                continue;
            }

            if (current.endpoint().compareTo(exclude.endpoint()) == 0)
            {
                continue;
            }

            boolean shouldAdd = true;
            for (int j = 0; j < dstIdx; j++)
            {
                if (current.endpoint().compareTo(dst[j].endpoint()) == 0)
                {
                    shouldAdd = false;
                    break;
                }
            }

            if (shouldAdd)
            {
                dst[dstIdx].wrap(current);
                dstIdx++;
            }
        }

        return dstIdx;
    }
}
