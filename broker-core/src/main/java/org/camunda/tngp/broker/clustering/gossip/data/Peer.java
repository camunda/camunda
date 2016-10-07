package org.camunda.tngp.broker.clustering.gossip.data;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;
import static org.camunda.tngp.management.gossip.BooleanType.*;
import static org.camunda.tngp.management.gossip.PeerDescriptorDecoder.BLOCK_LENGTH;
import static org.camunda.tngp.management.gossip.PeerDescriptorDecoder.SCHEMA_VERSION;
import static org.camunda.tngp.management.gossip.PeerDescriptorEncoder.hostHeaderLength;
import static org.camunda.tngp.management.gossip.PeerState.NULL_VAL;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.management.gossip.PeerDescriptorDecoder;
import org.camunda.tngp.management.gossip.PeerDescriptorEncoder;
import org.camunda.tngp.management.gossip.PeerState;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;


public class Peer implements BufferWriter, BufferReader, Comparable<Peer>
{
    public static final int MAX_PEER_LENGTH = 128;

    protected final PeerDescriptorDecoder decoder = new PeerDescriptorDecoder();
    protected final PeerDescriptorEncoder encoder = new PeerDescriptorEncoder();

    protected final Endpoint endPoint = new Endpoint();
    protected final Heartbeat heartbeat = new Heartbeat();
    protected PeerState state = NULL_VAL;
    protected long changeStateTime = -1;
    protected boolean localPeer = false;
    protected boolean locked = false;

    public Endpoint endpoint()
    {
        return endPoint;
    }

    public Heartbeat heartbeat()
    {
        return heartbeat;
    }

    public PeerState state()
    {
        return state;
    }

    public Peer state(final PeerState state)
    {
        this.state = state;
        return this;
    }

    public long changeStateTime()
    {
        return changeStateTime;
    }

    public Peer changeStateTime(final long changeStateTime)
    {
        this.changeStateTime = changeStateTime;
        return this;
    }

    public boolean localPeer()
    {
        return localPeer;
    }

    public Peer localPeer(final boolean localPeer)
    {
        this.localPeer = localPeer;
        return this;
    }

    public boolean locked()
    {
        return locked;
    }

    public Peer locked(final boolean locked)
    {
        this.locked = locked;
        return this;
    }

    @Override
    public int compareTo(Peer o)
    {
        return endPoint.compareTo(o.endpoint());
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        heartbeat()
            .generation(decoder.generation())
            .version(decoder.version());

        final int hostnameOffset = decoder.sbeBlockLength() + hostHeaderLength();
        final int hostnameLength = decoder.hostLength();

        endpoint()
            .host(buffer, hostnameOffset, hostnameLength)
            .port(decoder.port());

        this.state(decoder.state())
            .changeStateTime(decoder.changeStateTime())
            .localPeer(decoder.localPeer() == TRUE)
            .locked(decoder.locked() == TRUE);
    }

    public void wrap(final Peer peer)
    {
        heartbeat().wrap(peer.heartbeat());
        endpoint().wrap(peer.endpoint());

        this.state(peer.state())
            .changeStateTime(peer.changeStateTime())
            .localPeer(peer.localPeer())
            .locked(peer.locked());
    }

    @Override
    public int getLength()
    {
        return encoder.sbeBlockLength() +
                hostHeaderLength() +
                endPoint.hostLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        final Heartbeat heartbeat = heartbeat();
        final Endpoint endpoint = endpoint();

        final DirectBuffer hostBuffer = endpoint.getBuffer();

        encoder.wrap(buffer, offset)
            .port(endpoint.port())
            .state(state())
            .generation(heartbeat.generation())
            .version(heartbeat.version())
            .changeStateTime(changeStateTime())
            .localPeer(localPeer() ? TRUE : FALSE)
            .locked(locked() ? TRUE : FALSE)
            .putHost(hostBuffer, hostOffset(0), endpoint.hostLength());
    }

}
