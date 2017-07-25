/*
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
package io.zeebe.broker.clustering.gossip.data;

import java.util.Iterator;

import org.agrona.MutableDirectBuffer;

import io.zeebe.util.collection.CompactListIterator;

public class PeerListIterator implements Iterator<Peer>
{
    protected PeerList values;
    protected CompactListIterator iterator;

    protected final Peer curr = new Peer();

    public PeerListIterator(final PeerList values)
    {
        this.values = values;

        reset();
    }

    /**
     * Reset method for fixed values.
     */
    public void reset()
    {
        iterator = values.getPeers().iterator();
    }

    @Override
    public boolean hasNext()
    {
        return iterator.hasNext();
    }

    public Peer next()
    {
        final MutableDirectBuffer next = iterator.next();
        curr.wrap(next, 0, next.capacity());

        return curr;
    }

    public int position()
    {
        return iterator.position();
    }

}
