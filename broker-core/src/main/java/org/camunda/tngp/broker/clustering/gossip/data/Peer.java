package org.camunda.tngp.broker.clustering.gossip.data;

import static org.camunda.tngp.clustering.gossip.PeerDescriptorDecoder.BLOCK_LENGTH;
import static org.camunda.tngp.clustering.gossip.PeerDescriptorDecoder.SCHEMA_VERSION;
import static org.camunda.tngp.clustering.gossip.PeerState.ALIVE;
import static org.camunda.tngp.clustering.gossip.PeerState.DEAD;
import static org.camunda.tngp.clustering.gossip.PeerState.NULL_VAL;
import static org.camunda.tngp.clustering.gossip.PeerState.SUSPECT;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.clustering.gossip.EndpointType;
import org.camunda.tngp.clustering.gossip.PeerDescriptorDecoder;
import org.camunda.tngp.clustering.gossip.PeerDescriptorDecoder.EndpointsDecoder;
import org.camunda.tngp.clustering.gossip.PeerDescriptorEncoder;
import org.camunda.tngp.clustering.gossip.PeerDescriptorEncoder.EndpointsEncoder;
import org.camunda.tngp.clustering.gossip.PeerState;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class Peer implements BufferWriter, BufferReader, Comparable<Peer>
{
    public static final int PEER_ENDPOINT_COUNT = 3;

    public static final int MAX_PEER_LENGTH =
            PeerDescriptorEncoder.BLOCK_LENGTH +
            EndpointsEncoder.sbeHeaderSize() +
            PEER_ENDPOINT_COUNT * (
                EndpointsEncoder.sbeBlockLength() +
                EndpointsEncoder.hostHeaderLength() +
                SocketAddress.MAX_HOST_LENGTH
            );

    protected final PeerDescriptorDecoder decoder = new PeerDescriptorDecoder();
    protected final PeerDescriptorEncoder encoder = new PeerDescriptorEncoder();

    protected final SocketAddress clientEndpoint = new SocketAddress();
    protected final SocketAddress managementEndpoint = new SocketAddress();
    protected final SocketAddress replicationEndpoint = new SocketAddress();

    protected final Heartbeat heartbeat = new Heartbeat();
    protected PeerState state = NULL_VAL;

    protected long changeStateTime = -1L;

    public SocketAddress clientEndpoint()
    {
        return clientEndpoint;
    }

    public SocketAddress managementEndpoint()
    {
        return managementEndpoint;
    }

    public SocketAddress replicationEndpoint()
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

        for (final EndpointsDecoder endpointsDecoder : decoder.endpoints())
        {
            final SocketAddress endpoint;
            switch (endpointsDecoder.endpointType())
            {
                case CLIENT:
                    endpoint = clientEndpoint();
                    break;
                case MANAGEMENT:
                    endpoint = managementEndpoint();
                    break;
                case REPLICATION:
                    endpoint = replicationEndpoint();
                    break;
                default:
                    throw new RuntimeException("Unknown endpoint type for peer: " + endpointsDecoder.endpointType());
            }

            final MutableDirectBuffer hostBuffer = endpoint.getHostBuffer();
            final int hostLength = endpointsDecoder.hostLength();

            endpoint.port(endpointsDecoder.port());
            endpoint.hostLength(hostLength);
            endpointsDecoder.getHost(hostBuffer, 0, hostLength);
        }
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
                EndpointsDecoder.sbeHeaderSize() +
                PEER_ENDPOINT_COUNT * (EndpointsDecoder.sbeBlockLength() + EndpointsDecoder.hostHeaderLength()) +
                clientEndpoint().hostLength() +
                managementEndpoint().hostLength() +
                replicationEndpoint().hostLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        final Heartbeat heartbeat = heartbeat();

        final SocketAddress clientEndpoint = clientEndpoint();
        final SocketAddress managementEndpoint = managementEndpoint();
        final SocketAddress replicationEndpoint = replicationEndpoint();

        final DirectBuffer clientEndpointBuffer = clientEndpoint.getHostBuffer();
        final int clientHostLength = clientEndpoint.hostLength();

        final DirectBuffer managementEndpointBuffer = managementEndpoint.getHostBuffer();
        final int managementHostLength = managementEndpoint.hostLength();

        final DirectBuffer replicationEndpointBuffer = replicationEndpoint.getHostBuffer();
        final int replicationHostLength = replicationEndpoint.hostLength();

        final EndpointsEncoder endpointsEncoder = encoder.wrap(buffer, offset)
            .state(state())
            .generation(heartbeat.generation())
            .version(heartbeat.version())
            .changeStateTime(changeStateTime())
            .endpointsCount(PEER_ENDPOINT_COUNT);

        endpointsEncoder.next()
            .endpointType(EndpointType.CLIENT)
            .port(clientEndpoint.port())
            .putHost(clientEndpointBuffer, 0, clientHostLength);

        endpointsEncoder.next()
            .endpointType(EndpointType.MANAGEMENT)
            .port(managementEndpoint.port())
            .putHost(managementEndpointBuffer, 0, managementHostLength);

        endpointsEncoder.next()
            .endpointType(EndpointType.REPLICATION)
            .port(replicationEndpoint.port())
            .putHost(replicationEndpointBuffer, 0, replicationHostLength);
    }

    public void reset()
    {
        heartbeat().generation(PeerDescriptorEncoder.generationNullValue());
        heartbeat().version(PeerDescriptorEncoder.versionNullValue());

        clientEndpoint().reset();
        clientEndpoint().port(EndpointsDecoder.portNullValue());

        managementEndpoint().reset();
        managementEndpoint().port(EndpointsDecoder.portNullValue());

        replicationEndpoint().reset();
        replicationEndpoint().port(EndpointsDecoder.portNullValue());

        state = NULL_VAL;
        changeStateTime = -1L;
    }

    @Override
    public String toString()
    {
        return "Peer{" +
            "clientEndpoint=" + clientEndpoint +
            ", managementEndpoint=" + managementEndpoint +
            ", replicationEndpoint=" + replicationEndpoint +
            ", heartbeat=" + heartbeat +
            ", state=" + state +
            ", changeStateTime=" + changeStateTime +
            '}';
    }
}
