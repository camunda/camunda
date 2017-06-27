package io.zeebe.broker.clustering.gossip.message.util;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.RaftMembershipList;
import io.zeebe.clustering.gossip.GossipDecoder;
import io.zeebe.clustering.gossip.GossipDecoder.PeersDecoder;
import io.zeebe.clustering.gossip.GossipDecoder.PeersDecoder.EndpointsDecoder;
import io.zeebe.clustering.gossip.GossipDecoder.PeersDecoder.RaftMembershipsDecoder;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferReader;

public class GossipMessageReader implements BufferReader, Iterator<Peer>
{
    private Iterator<PeersDecoder> iterator;

    private final GossipDecoder bodyDecoder = new GossipDecoder();

    private final Peer currentPeer = new Peer();

    @Override
    public void wrap(final DirectBuffer values, final int offset, final int length)
    {
        bodyDecoder.wrap(values, offset, GossipDecoder.BLOCK_LENGTH, GossipDecoder.SCHEMA_VERSION);
        iterator = bodyDecoder.peers().iterator();
    }

    @Override
    public boolean hasNext()
    {
        return iterator.hasNext();
    }

    @Override
    public Peer next()
    {
        final PeersDecoder decoder = iterator.next();

        currentPeer.reset();

        currentPeer.heartbeat()
            .generation(decoder.generation())
            .version(decoder.version());

        for (final EndpointsDecoder endpointsDecoder : decoder.endpoints())
        {
            final SocketAddress endpoint;
            switch (endpointsDecoder.endpointType())
            {
                case CLIENT:
                    endpoint = currentPeer.clientEndpoint();
                    break;
                case MANAGEMENT:
                    endpoint = currentPeer.managementEndpoint();
                    break;
                case REPLICATION:
                    endpoint = currentPeer.replicationEndpoint();
                    break;
                default:
                    throw new RuntimeException("Unknown endpoint type for peer: " + endpointsDecoder.endpointType());
            }

            final MutableDirectBuffer hostBuffer = endpoint.getHostBuffer();
            final int hostLength = endpointsDecoder.hostLength();

            endpoint.port(endpointsDecoder.port());
            endpoint.hostLength(hostLength);
            endpointsDecoder.getHost(hostBuffer, 0, hostLength);
            endpoint.host();
        }

        final RaftMembershipList raftMemberships = currentPeer.raftMemberships();
        for (final RaftMembershipsDecoder raftMembershipsDecoder : decoder.raftMemberships())
        {
            raftMemberships.add(raftMembershipsDecoder);
        }

        currentPeer.state(decoder.state())
            .changeStateTime(-1L);

        return currentPeer;
    }

}
