package org.camunda.tngp.broker.clustering.gossip.data;

import static org.camunda.tngp.clustering.gossip.PeerDescriptorDecoder.*;
import static org.camunda.tngp.clustering.gossip.PeerState.*;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.clustering.gossip.PeerDescriptorDecoder;
import org.camunda.tngp.clustering.gossip.PeerDescriptorEncoder;
import org.camunda.tngp.clustering.gossip.PeerState;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class Peer implements BufferWriter, BufferReader, Comparable<Peer>
{
    public static final int MAX_PEER_LENGTH = BLOCK_LENGTH +
            clientHostHeaderLength() +
            managementHostHeaderLength() +
            replicationHostHeaderLength() +
            (Endpoint.MAX_HOST_LENGTH * 3);

    protected final PeerDescriptorDecoder decoder = new PeerDescriptorDecoder();
    protected final PeerDescriptorEncoder encoder = new PeerDescriptorEncoder();

    protected final Endpoint clientEndpoint = new Endpoint();
    protected final Endpoint managementEndpoint = new Endpoint();
    protected final Endpoint replicationEndpoint = new Endpoint();

    protected final Heartbeat heartbeat = new Heartbeat();
    protected PeerState state = NULL_VAL;

    protected long changeStateTime = -1L;

    public Endpoint clientEndpoint()
    {
        return clientEndpoint;
    }

    public Endpoint managementEndpoint()
    {
        return managementEndpoint;
    }

    public Endpoint replicationEndpoint()
    {
        return replicationEndpoint;
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

    public Peer alive()
    {
        if (state != ALIVE)
        {
            state = ALIVE;
            changeStateTime = System.currentTimeMillis();
        }
        return this;
    }

    public Peer suspect()
    {
        if (state != SUSPECT)
        {
            state = SUSPECT;
            changeStateTime = System.currentTimeMillis();
        }
        return this;
    }

    public Peer dead()
    {
        if (state != DEAD)
        {
            state = DEAD;
            changeStateTime = System.currentTimeMillis();
        }
        return this;

    }

    @Override
    public int compareTo(Peer o)
    {
        return managementEndpoint.compareTo(o.managementEndpoint());
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        decoder.wrap(buffer, offset, BLOCK_LENGTH, SCHEMA_VERSION);

        state(decoder.state()).changeStateTime(decoder.changeStateTime());

        heartbeat()
            .generation(decoder.generation())
            .version(decoder.version());

        final int clientPort = decoder.clientPort();
        final int managementPort = decoder.managementPort();
        final int replicationPort = decoder.replicationPort();

        final int clientHostLength = decoder.clientHostLength();
        final Endpoint clientEndpoint = clientEndpoint();
        clientEndpoint.port(clientPort);
        clientEndpoint.hostLength(clientHostLength);
        decoder.getClientHost(clientEndpoint.getHostBuffer(), 0, clientHostLength);

        final int managementHostLength = decoder.managementHostLength();
        final Endpoint managementEndpoint = managementEndpoint();
        managementEndpoint.port(managementPort);
        managementEndpoint.hostLength(managementHostLength);
        decoder.getManagementHost(managementEndpoint.getHostBuffer(), 0, managementHostLength);

        final int replicationHostLength = decoder.replicationHostLength();
        final Endpoint replicationEndpoint = replicationEndpoint();
        replicationEndpoint.port(replicationPort);
        replicationEndpoint.hostLength(replicationHostLength);
        decoder.getReplicationHost(replicationEndpoint.getHostBuffer(), 0, replicationHostLength);
    }

    public void wrap(final Peer peer)
    {
        heartbeat().wrap(peer.heartbeat());

        clientEndpoint().wrap(peer.clientEndpoint());
        managementEndpoint().wrap(peer.managementEndpoint());
        replicationEndpoint().wrap(peer.replicationEndpoint());

        this.state(peer.state()).changeStateTime(peer.changeStateTime());
    }

    @Override
    public int getLength()
    {
        return encoder.sbeBlockLength() +
                clientHostHeaderLength() +
                clientEndpoint().hostLength() +
                managementHostHeaderLength() +
                managementEndpoint().hostLength() +
                replicationHostHeaderLength() +
                replicationEndpoint().hostLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        final Heartbeat heartbeat = heartbeat();

        final Endpoint clientEndpoint = clientEndpoint();
        final Endpoint managementEndpoint = managementEndpoint();
        final Endpoint replicationEndpoint = replicationEndpoint();

        final DirectBuffer clientEndpointBuffer = clientEndpoint.getHostBuffer();
        final int clientHostLength = clientEndpoint.hostLength();

        final DirectBuffer managementEndpointBuffer = managementEndpoint.getHostBuffer();
        final int managementHostLength = managementEndpoint.hostLength();

        final DirectBuffer replicationEndpointBuffer = replicationEndpoint.getHostBuffer();
        final int replicationHostLength = replicationEndpoint.hostLength();

        encoder.wrap(buffer, offset)
            .state(state())
            .generation(heartbeat.generation())
            .version(heartbeat.version())
            .changeStateTime(changeStateTime())
            .clientPort(clientEndpoint.port())
            .managementPort(managementEndpoint.port())
            .replicationPort(replicationEndpoint.port())
            .putClientHost(clientEndpointBuffer, 0, clientHostLength)
            .putManagementHost(managementEndpointBuffer, 0, managementHostLength)
            .putReplicationHost(replicationEndpointBuffer, 0, replicationHostLength);
    }

    public void reset()
    {
        heartbeat().generation(PeerDescriptorEncoder.generationNullValue());
        heartbeat().version(PeerDescriptorEncoder.versionNullValue());

        clientEndpoint().reset();
        clientEndpoint().port(PeerDescriptorEncoder.clientPortNullValue());

        managementEndpoint().reset();
        managementEndpoint().port(PeerDescriptorEncoder.managementPortNullValue());

        replicationEndpoint().reset();
        replicationEndpoint().port(PeerDescriptorEncoder.replicationPortNullValue());

        state = NULL_VAL;
        changeStateTime = -1L;
    }
}
