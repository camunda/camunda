/**
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.gossip.protocol.util;

import static io.zeebe.clustering.gossip.PeerState.ALIVE;
import static io.zeebe.clustering.gossip.PeerState.DEAD;

import java.util.Random;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerListIterator;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.transport.SocketAddress;

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

            final SocketAddress currEndpoint = curr.managementEndpoint();
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
        final SocketAddress peerEndpoint = peer.managementEndpoint();
        for (int i = 0; i < exclusions.length; i++)
        {
            final SocketAddress excludedEndpoint = exclusions[i].managementEndpoint();
            if (excludedEndpoint.compareTo(peerEndpoint) == 0)
            {
                return true;
            }
        }
        return false;
    }

}
