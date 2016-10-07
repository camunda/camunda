package org.camunda.tngp.broker.clustering.gossip.data;

import static org.camunda.tngp.broker.clustering.gossip.data.Peer.*;
import static org.camunda.tngp.management.gossip.PeerState.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.clustering.gossip.GossipListener;
import org.camunda.tngp.list.CompactList;
import org.camunda.tngp.management.gossip.PeerDescriptorDecoder;

/**
 * <p>An instance of {@link PeerManager} contains a list of peers.</p>
 */
public class PeerList implements Iterable<Peer>
{
    protected static final Comparator<DirectBuffer> PEER_COMPARATOR = new Comparator<DirectBuffer>()
    {
        protected final Peer peer1 = new Peer();
        protected final Peer peer2 = new Peer();

        @Override
        public int compare(final DirectBuffer o1, final DirectBuffer o2)
        {
            peer1.wrap(o1, 0, o1.capacity());
            peer2.wrap(o2, 0, o2.capacity());
            return peer1.compareTo(peer2);
        }
    };

    protected static final int MAX_PEER_CAPACITY = 1000;
    protected final CompactList underlyingList;

    protected final PeerListIterator iterator;

    protected final UnsafeBuffer tmpPeerBuffer = new UnsafeBuffer(new byte[MAX_PEER_LENGTH]);
    protected final Peer tmpPeer = new Peer();
    protected final Random random = new Random();
    protected final EpochClock clock = new SystemEpochClock();

    protected final List<GossipListener> gossipListeners = new CopyOnWriteArrayList<>();

    public PeerList(final int capacity)
    {
        this(new CompactList(MAX_PEER_LENGTH, capacity, (c) -> ByteBuffer.allocateDirect(c)));
    }

    public PeerList(final CompactList underlyingList)
    {
        this.underlyingList = underlyingList;
        this.iterator = new PeerListIterator(this);
    }
    /**
     * <p>Returns a {@link CompactList list} containing peer information.</p>
     *
     * <p>To decode the peer information the {@link PeerDescriptorDecoder} must be used.</p>
     *
     * @see CompactList
     * @return a {@link CompactList} containing peer information.
     */
    public CompactList getPeers()
    {
        return underlyingList;
    }

    /**
     * Merge the passed updates into this list of peers, so that
     * the ascending order of the list is kept.
     *
     * @param updates to merge.
     */
    public void merge(final Iterator<Peer> updates)
    {
        merge(updates, null);
    }
    /**
     * Merge the passed updates into this list of peers, so that
     * the ascending order of the list is kept.
     *
     * If the passed updates does not contain a peer which is contained
     * by this list, then this peer is added to the passed list {@code diff}.
     *
     * If the passed updates contains outdated information to a peer, then
     * the updated information are added to the passed list {@code diff}.
     *
     * @param updates to merge.
     * @param diff to determine the diff between this list and the updates.
     */
    public void merge(final Iterator<Peer> updates, final PeerList diff)
    {
        final PeerListIterator thisIterator = iterator();

        if (thisIterator.hasNext())
        {
            Peer thatPeer = updates.hasNext() ? updates.next() : null;
            boolean keepPosition = false;
            Peer thisPeer = null;


            do
            {
                thisPeer = !keepPosition ? thisIterator.next() : thisPeer;
                keepPosition = false;

                int cmp = -1;
                if (thatPeer != null)
                {
                    cmp = thisPeer.compareTo(thatPeer);
                }

                if (cmp < 0)
                {
                    if (diff != null)
                    {
                        diff.append(thisPeer);
                    }

                    if (!thisIterator.hasNext() && thatPeer != null)
                    {
                        append(thatPeer);
                    }
                }
                else if (cmp > 0)
                {
                    if (thatPeer.state() == ALIVE)
                    {
                        thisIterator.add(thatPeer);
                        for (int i = 0; i < gossipListeners.size(); i++)
                        {
                            gossipListeners.get(i).onPeerJoin(thatPeer);
                        }
                    }
                    else
                    {
                        keepPosition = true;
                    }
                    thatPeer = updates.hasNext() ? updates.next() : null;
                }
                else
                {
                    if (mergeState(thisPeer, thatPeer))
                    {
                        if (diff != null)
                        {
                            diff.append(thisPeer);
                        }
                    }

                    if (thisIterator.hasNext() && updates.hasNext())
                    {
                        thatPeer = updates.next();
                    }
                    else
                    {
                        thatPeer = null;
                    }
                }
            }
            while (thisIterator.hasNext());
        }

        while (updates.hasNext())
        {
            final Peer peer = updates.next();
            append(peer);
        }

    }

    protected boolean mergeState(final Peer thisPeer, final Peer thatPeer)
    {
        if (thisPeer.localPeer() && thatPeer.state() != ALIVE)
        {
            // refute
            thisPeer.state(ALIVE)
                .heartbeat().generation(clock.time());
            return true;
        }

        final Heartbeat thisHeartbeat = thisPeer.heartbeat();
        final Heartbeat thatHeartbeat = thatPeer.heartbeat();

        final int cmp = thisHeartbeat.compareTo(thatHeartbeat);

        switch (thatPeer.state())
        {
            case ALIVE:
            {
                if (cmp < 0)
                {
                    thisHeartbeat.wrap(thatHeartbeat);
                    markPeerAsAlive(thisPeer);
                    set(thisPeer);
                }
                break;
            }

            case SUSPECT:
            {
                if (thisPeer.state() == SUSPECT && cmp < 0)
                {
                    thisHeartbeat.wrap(thatHeartbeat);
                    set(thisPeer);
                }
                else if (thisPeer.state() == ALIVE && !(cmp > 0))
                {
                    thisHeartbeat.wrap(thatHeartbeat);
                    markPeerAsSuspected(thisPeer);
                    set(thisPeer);
                }
                break;
            }

            case DEAD:
            {
                if (!(cmp > 0))
                {
                    thisHeartbeat.wrap(thatHeartbeat);
                    markPeerAsDead(thisPeer);
                    set(thisPeer);
                }
                break;
            }

            default:
            {
                // nothing to do;
            }
        }

        return cmp > 0;
    }

    /**
     * Return the peer at the passed idx.
     *
     * @param idx at which index.
     * @param dst to wrap the values.
     */
    public void get(final int idx, final Peer dst)
    {
        underlyingList.get(idx, tmpPeerBuffer, 0);
        dst.wrap(tmpPeerBuffer, 0, tmpPeerBuffer.capacity());
    }

    /**
     * Append the passed peer to the end of the list.
     *
     * @param peer to append.
     */
    public void append(final Peer peer)
    {
        peer.write(tmpPeerBuffer, 0);
        underlyingList.add(tmpPeerBuffer, 0, peer.getLength());

        for (int i = 0; i < gossipListeners.size(); i++)
        {
            gossipListeners.get(i).onPeerJoin(peer);
        }
    }

    /**
     * Insert the passed peer to the list. This insert guarantee that
     * the ascending order of the list is kept.
     *
     * Note: If the peer is already present in the list, than the insert
     * is omitted.
     *
     * @param peer to insert.
     */
    public void insert(final Peer peer)
    {
        final int idx = find(peer);
        if (idx < 0)
        {
            underlyingList.add(tmpPeerBuffer, 0, peer.getLength(), ~idx);

            for (int i = 0; i < gossipListeners.size(); i++)
            {
                gossipListeners.get(i).onPeerJoin(peer);
            }
        }
    }

    /**
     * Replace the values of an existing peer with the passed peer.
     *
     * Note: If the peer is not found in the list, than the set
     * is omitted.
     *
     * @param peer to set.
     */
    public void set(final Peer peer)
    {
        final int idx = find(peer);
        if (idx > -1)
        {
            underlyingList.set(idx, tmpPeerBuffer, 0, peer.getLength());
        }
    }

    /**
     * Searches the list for the specified peer.
     *
     * @see CompactList#find(org.agrona.DirectBuffer, Comparator)
     */
    public int find(final Peer peer)
    {
        peer.write(tmpPeerBuffer, 0);
        return underlyingList.find(tmpPeerBuffer, PEER_COMPARATOR);
    }

    /**
     * Update the heartbeat of the passed peer and replace
     * the existing peer in the list with the passed peer.
     *
     * @param peer to update the peer.
     */
    public void updateHeartbeat(final Peer peer)
    {
        final Heartbeat heartbeat = peer.heartbeat();
        heartbeat.version(heartbeat.version() + 1);
        set(peer);
    }

    public void markPeerAsAlive(final Peer peer)
    {
        if (peer.state() != ALIVE)
        {
            peer.state(ALIVE)
                .changeStateTime(clock.time());
            set(peer);
        }
    }

    public void markPeerAsSuspected(final Peer peer)
    {
        if (peer.state() == ALIVE)
        {
            peer.state(SUSPECT)
                .changeStateTime(clock.time());
            set(peer);
        }
    }

    public void markPeerAsDead(final Peer peer)
    {
        if (peer.state() != DEAD)
        {
            peer.state(DEAD)
                .changeStateTime(clock.time());
            set(peer);
        }
    }
    /**
     * Clear the list of peers.
     */
    public void clear()
    {
        underlyingList.clear();
    }

    /**
     * Return the size of the list of peers.
     *
     * @return the size of the list.
     */
    public int size()
    {
        return underlyingList.size();
    }

    public int capacity()
    {
        return underlyingList.capacity();
    }

    public void shuffle()
    {
        final int size = size();
        for (int i = size; i > 1; i--)
        {
            swap(i - 1, random.nextInt(i));
        }
    }

    public void swap(final int i, final int j)
    {
        get(i, tmpPeer);

        underlyingList.get(j, tmpPeerBuffer, 0);
        underlyingList.set(i, tmpPeerBuffer);

        tmpPeer.write(tmpPeerBuffer, 0);
        underlyingList.set(j, tmpPeerBuffer);
    }

    public void addAll(final PeerList peerList)
    {
        final PeerListIterator iterator = peerList.iterator();
        while (iterator.hasNext())
        {
            append(iterator.next());
        }
    }

    /**
     * <p>Returns an iterator over peer elements of this list of peers.</p>
     *
     * @return an {@link Iterator}.
     */
    @Override
    public PeerListIterator iterator()
    {
        iterator.reset();

        return iterator;
    }

    public InputStream toInputStream()
    {
        return underlyingList.toInputStream();
    }

    public void registerGossipListener(final GossipListener listener)
    {
        gossipListeners.add(listener);
    }
}
