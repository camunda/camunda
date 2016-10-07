package org.camunda.tngp.broker.clustering.gossip.data;

import java.util.Iterator;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.list.CompactListIterator;

public class PeerListIterator implements Iterator<Peer>
{
    protected PeerList values;
    protected CompactListIterator iterator;

    protected final Peer currentPeer = new Peer();

    protected final UnsafeBuffer tmpPeerBuffer = new UnsafeBuffer(new byte[Peer.MAX_PEER_LENGTH]);

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
        currentPeer.wrap(next, 0, next.capacity());

        return currentPeer;
    }

    /**
     * Inserts the specified peer into the list. The peer is
     * inserted before the last peer returned by {@link #next}.
     *
     * Note that the position of this iterator does not change.
     *
     * @param peer the peer to insert
     * @throws IllegalStateException if {@code next} has not been called
     */
    public void add(Peer peer)
    {
        final int position = iterator.position();
        if (position < 0)
        {
            throw new IllegalStateException();
        }

        peer.write(tmpPeerBuffer, 0);
        values.getPeers().add(tmpPeerBuffer, 0, peer.getLength(), position);
    }

    /**
     * Replaces the last peer returned by {@link #next} with the specified peer.
     *
     * @param peer the peer with which to replace the last peer returned by
     *          {@code next}
     * @throws IllegalStateException if {@code next} has not been called
     */
    public void set(Peer peer)
    {
        final int position = iterator.position();
        if (position < 0)
        {
            throw new IllegalStateException();
        }

        peer.write(tmpPeerBuffer, 0);
        values.getPeers().set(position, tmpPeerBuffer, 0, peer.getLength());
    }

}
