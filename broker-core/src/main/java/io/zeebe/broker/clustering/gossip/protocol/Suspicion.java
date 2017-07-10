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
package io.zeebe.broker.clustering.gossip.protocol;

import static io.zeebe.clustering.gossip.PeerState.SUSPECT;

import java.util.concurrent.TimeUnit;

import io.zeebe.broker.clustering.gossip.GossipContext;
import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerListIterator;

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
