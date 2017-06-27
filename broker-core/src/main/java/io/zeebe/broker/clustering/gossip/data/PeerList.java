package io.zeebe.broker.clustering.gossip.data;

import static io.zeebe.broker.clustering.gossip.data.Peer.MAX_PEER_LENGTH;
import static io.zeebe.clustering.gossip.PeerState.ALIVE;
import static io.zeebe.clustering.gossip.PeerState.SUSPECT;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.clustering.gossip.PeerDescriptorDecoder;
import io.zeebe.list.CompactList;

/**
 * <p>An instance of {@link PeerList} contains a list of peers.</p>
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

    protected final CompactList underlyingList;

    protected final PeerListIterator iterator;
    protected final PeerListIterator localIterator;

    protected final UnsafeBuffer tmpPeerBuffer = new UnsafeBuffer(new byte[MAX_PEER_LENGTH]);
    protected final UnsafeBuffer tmpPeerBufferView = new UnsafeBuffer(0, 0);

    protected final Peer shuffledPeer = new Peer();
    protected final Random shuffleRandom = new Random();

    protected final List<PeerListListener> listeners = new CopyOnWriteArrayList<>();

    public PeerList(final int capacity)
    {
        this(new CompactList(MAX_PEER_LENGTH, capacity, (c) -> ByteBuffer.allocateDirect(c)));
    }

    public PeerList(final CompactList underlyingList)
    {
        this.underlyingList = underlyingList;
        this.iterator = new PeerListIterator(this);
        this.localIterator = new PeerListIterator(this);
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
        localIterator.reset();

        if (localIterator.hasNext())
        {
            Peer thatPeer = updates.hasNext() ? updates.next() : null;
            boolean keepPosition = false;
            Peer thisPeer = null;

            do
            {
                thisPeer = !keepPosition ? localIterator.next() : thisPeer;
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

                    if (!localIterator.hasNext() && thatPeer != null)
                    {
                        append(thatPeer);
                    }
                }
                else if (cmp > 0)
                {
                    if (thatPeer.state() == ALIVE)
                    {
                        add(localIterator.position(), thatPeer);
                    }
                    else
                    {
                        keepPosition = true;
                    }
                    thatPeer = updates.hasNext() ? updates.next() : null;
                }
                else
                {
                    if (mergePeer(thisPeer, thatPeer, localIterator.position()))
                    {
                        if (diff != null)
                        {
                            diff.append(thisPeer);
                        }
                    }

                    if (localIterator.hasNext() && updates.hasNext())
                    {
                        thatPeer = updates.next();
                    }
                    else
                    {
                        thatPeer = null;
                    }
                }
            }
            while (localIterator.hasNext());
        }

        while (updates.hasNext())
        {
            final Peer peer = updates.next();
            append(peer);
        }

    }

    protected boolean mergePeer(final Peer thisPeer, final Peer thatPeer, final int idx)
    {
        final Heartbeat thisHeartbeat = thisPeer.heartbeat();
        final Heartbeat thatHeartbeat = thatPeer.heartbeat();

        final int cmp = thisHeartbeat.compareTo(thatHeartbeat);

        if (cmp < 0)
        {
            thisPeer.clientEndpoint().wrap(thatPeer.clientEndpoint());
            thisPeer.managementEndpoint().wrap(thatPeer.managementEndpoint());
            thisPeer.replicationEndpoint().wrap(thatPeer.replicationEndpoint());
            thisPeer.raftMemberships(thatPeer.raftMemberships());
        }

        switch (thatPeer.state())
        {
            case ALIVE:
            {
                if (cmp < 0)
                {
                    thisHeartbeat.wrap(thatHeartbeat);
                    thisPeer.alive();
                    set(idx, thisPeer);
                }
                break;
            }

            case SUSPECT:
            {
                if (thisPeer.state() == SUSPECT && cmp < 0)
                {
                    thisHeartbeat.wrap(thatHeartbeat);
                    set(idx, thisPeer);
                }
                else if (thisPeer.state() == ALIVE && !(cmp > 0))
                {
                    thisHeartbeat.wrap(thatHeartbeat);
                    thisPeer.suspect();
                    set(idx, thisPeer);
                }
                break;
            }

            case DEAD:
            {
                if (!(cmp > 0))
                {
                    thisHeartbeat.wrap(thatHeartbeat);
                    thisPeer.dead();
                    set(idx, thisPeer);
                }
                break;
            }

            default:
            {
                if (cmp < 0)
                {
                    set(idx, thisPeer);
                }
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
        final int length = underlyingList.get(idx, tmpPeerBuffer, 0);
        dst.wrap(tmpPeerBuffer, 0, length);
    }

    public void set(final int idx, final Peer src)
    {
        src.write(tmpPeerBuffer, 0);
        underlyingList.set(idx, tmpPeerBuffer, 0, src.getLength());
    }

    public void add(final int idx, final Peer peer)
    {
        peer.write(tmpPeerBuffer, 0);
        underlyingList.add(tmpPeerBuffer, 0, peer.getLength(), idx);

        for (int i = 0; i < listeners.size(); i++)
        {
            listeners.get(i).onPeerJoin(peer);
        }
    }

    /**
     * Append the passed peer to the end of the list.
     *
     * @param peer to append.
     */
    public void append(final Peer peer)
    {
        add(size(), peer);
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
            add(~idx, peer);
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
    public void update(final Peer peer)
    {
        final int idx = find(peer);
        if (idx > -1)
        {
            set(idx, peer);
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

        // limit the accessible memory to peer length to prevent reading out of bounce
        tmpPeerBufferView.wrap(tmpPeerBuffer, 0, peer.getLength());

        final int index = underlyingList.find(tmpPeerBufferView, PEER_COMPARATOR);

        // reset view to release reference
        tmpPeerBufferView.wrap(0, 0);

        return index;
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

    public int sizeVolatile()
    {
        return underlyingList.sizeVolatile();
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
            swap(i - 1, shuffleRandom.nextInt(i));
        }
    }

    public void swap(final int i, final int j)
    {
        get(i, shuffledPeer);

        final int length = underlyingList.get(j, tmpPeerBuffer, 0);
        underlyingList.set(i, tmpPeerBuffer, 0, length);

        shuffledPeer.write(tmpPeerBuffer, 0);
        underlyingList.set(j, tmpPeerBuffer, 0, shuffledPeer.getLength());
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

    public void registerListener(final PeerListListener listener)
    {
        listeners.add(listener);
    }

    public void removeListener(final PeerListListener listener)
    {
        listeners.remove(listener);
    }


    @Override
    public String toString()
    {
        return "PeerList{" +
            "size=" + underlyingList.size() +
            ", elements=" +
                StreamSupport.stream(this.spliterator(), false)
                    .map(Peer::toString)
                    .collect(Collectors.joining(", ", "[", "]")) +

            '}';
    }

}
