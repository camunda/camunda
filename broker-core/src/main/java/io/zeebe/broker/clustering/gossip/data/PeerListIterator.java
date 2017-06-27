package io.zeebe.broker.clustering.gossip.data;

import java.util.Iterator;

import org.agrona.MutableDirectBuffer;

import io.zeebe.list.CompactListIterator;

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
