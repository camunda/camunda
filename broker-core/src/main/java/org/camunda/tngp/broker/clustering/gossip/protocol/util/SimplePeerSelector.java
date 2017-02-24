package org.camunda.tngp.broker.clustering.gossip.protocol.util;

import static org.camunda.tngp.clustering.gossip.PeerState.*;

import java.util.Random;

import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerListIterator;
import org.camunda.tngp.broker.clustering.gossip.data.PeerSelector;

public class SimplePeerSelector implements PeerSelector
{
    private final Random random = new Random();

    private final PeerList peers;
    private final PeerList shuffled;
    private final PeerListIterator iterator;

    private final Peer curr = new Peer();

    public SimplePeerSelector(final PeerList peers)
    {
        this.peers = peers;
        this.shuffled = new PeerList(peers.capacity());
        this.shuffled.addAll(peers);
        this.iterator = shuffled.iterator();

        peers.registerListener((p) ->
        {
            shuffled.append(p);
        });
    }

    public boolean next(final Peer dst, final Peer[] exclusions)
    {
        for (int i = 0; i < peers.size(); i++)
        {
            if (!iterator.hasNext())
            {
                shuffled.shuffle();
                iterator.reset();
            }

            if (iterator.hasNext())
            {
                curr.wrap(iterator.next());

                final int idx = peers.find(curr);
                if (idx >= 0)
                {
                    peers.get(idx, curr);

                    if (curr.state() != DEAD)
                    {
                        if (exclusions != null && isExcluded(curr, exclusions))
                        {
                            break;
                        }

                        dst.reset();
                        dst.wrap(curr);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public int next(final int max, final Peer[] dst, final Peer[] exclusions)
    {
        final int n = shuffled.size();
        int dstIdx = 0;

        for (int i = 0; i < n * 3 && dstIdx < max; i++)
        {
            final int idx = random.nextInt(n);
            shuffled.get(idx, curr);

            final Endpoint currEndpoint = curr.managementEndpoint();
            if (curr.state() != ALIVE || isExcluded(curr, exclusions))
            {
                continue;
            }

            boolean shouldAdd = true;
            for (int j = 0; j < dstIdx; j++)
            {
                if (currEndpoint.compareTo(dst[j].managementEndpoint()) == 0)
                {
                    shouldAdd = false;
                    break;
                }
            }

            if (shouldAdd)
            {
                // get current state from peer list
                final int peerIdx = peers.find(curr);
                if (idx >= 0)
                {
                    peers.get(peerIdx, curr);

                    dst[dstIdx].reset();
                    dst[dstIdx].wrap(curr);
                    dstIdx++;
                }
            }
        }

        return dstIdx;
    }

    protected boolean isExcluded(final Peer peer, final Peer[] exclusions)
    {
        final Endpoint peerEndpoint = peer.managementEndpoint();
        for (int i = 0; i < exclusions.length; i++)
        {
            final Endpoint excludedEndpoint = exclusions[i].managementEndpoint();
            if (excludedEndpoint.compareTo(peerEndpoint) == 0)
            {
                return true;
            }
        }
        return false;
    }

}
