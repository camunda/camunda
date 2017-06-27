package io.zeebe.broker.clustering.gossip.message.util;

import static io.zeebe.broker.clustering.gossip.data.Peer.PEER_ENDPOINT_COUNT;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.broker.clustering.gossip.data.Heartbeat;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.RaftMembership;
import io.zeebe.broker.clustering.gossip.data.RaftMembershipList;
import io.zeebe.clustering.gossip.EndpointType;
import io.zeebe.clustering.gossip.GossipDecoder.PeersDecoder;
import io.zeebe.clustering.gossip.GossipDecoder.PeersDecoder.EndpointsDecoder;
import io.zeebe.clustering.gossip.GossipEncoder;
import io.zeebe.clustering.gossip.GossipEncoder.PeersEncoder;
import io.zeebe.clustering.gossip.GossipEncoder.PeersEncoder.EndpointsEncoder;
import io.zeebe.clustering.gossip.GossipEncoder.PeersEncoder.RaftMembershipsEncoder;
import io.zeebe.clustering.gossip.MessageHeaderEncoder;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferWriter;

public class GossipMessageWriter implements BufferWriter
{
    protected PeerList peers;

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final GossipEncoder bodyEncoder = new GossipEncoder();

    public GossipMessageWriter peers(final PeerList peers)
    {
        this.peers = peers;
        return this;
    }

    @Override
    public int getLength()
    {
        final int size = peers.size();

        final int peerEndpointsLength =
            EndpointsDecoder.sbeHeaderSize() + PEER_ENDPOINT_COUNT * (EndpointsDecoder.sbeBlockLength() + EndpointsDecoder.hostHeaderLength());

        final int peersLength =
            PeersDecoder.sbeHeaderSize() + size * (PeersDecoder.sbeBlockLength() + peerEndpointsLength);

        int length = bodyEncoder.sbeBlockLength() + peersLength;

        final Iterator<Peer> iterator = peers.iterator();
        while (iterator.hasNext())
        {
            final Peer current = iterator.next();
            length += current.clientEndpoint().hostLength();
            length += current.managementEndpoint().hostLength();
            length += current.replicationEndpoint().hostLength();
            length += RaftMembershipsEncoder.sbeHeaderSize();

            for (final RaftMembership raftMembership : current.raftMemberships())
            {
                length +=
                    RaftMembershipsEncoder.sbeBlockLength() +
                    RaftMembershipsEncoder.topicNameHeaderLength() +
                    raftMembership.topicNameLength();
            }
        }

        return length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        final PeersEncoder encoder = bodyEncoder.wrap(buffer, offset).peersCount(peers.size());

        final Iterator<Peer> iterator = peers.iterator();
        while (iterator.hasNext())
        {
            final Peer current = iterator.next();

            final Heartbeat heartbeat = current.heartbeat();

            final SocketAddress clientEndpoint = current.clientEndpoint();
            final SocketAddress managementEndpoint = current.managementEndpoint();
            final SocketAddress replicationEndpoint = current.replicationEndpoint();

            final DirectBuffer clientHostBuffer = clientEndpoint.getHostBuffer();
            final int clientHostLength = clientEndpoint.hostLength();
            final int clientPort = clientEndpoint.port();

            final DirectBuffer managementHostBuffer = managementEndpoint.getHostBuffer();
            final int managementHostLength = managementEndpoint.hostLength();
            final int managementPort = managementEndpoint.port();

            final DirectBuffer replicationHostBuffer = replicationEndpoint.getHostBuffer();
            final int replicationHostLength = replicationEndpoint.hostLength();
            final int replicationPort = replicationEndpoint.port();

            final EndpointsEncoder endpointsEncoder = encoder.next()
                .state(current.state())
                .generation(heartbeat.generation())
                .version(heartbeat.version())
                .endpointsCount(PEER_ENDPOINT_COUNT);

            endpointsEncoder.next()
                .endpointType(EndpointType.CLIENT)
                .port(clientPort)
                .putHost(clientHostBuffer, 0, clientHostLength);

            endpointsEncoder.next()
                .endpointType(EndpointType.MANAGEMENT)
                .port(managementPort)
                .putHost(managementHostBuffer, 0, managementHostLength);

            endpointsEncoder.next()
                .endpointType(EndpointType.REPLICATION)
                .port(replicationPort)
                .putHost(replicationHostBuffer, 0, replicationHostLength);

            final RaftMembershipList raftMemberships = current.raftMemberships();
            final RaftMembershipsEncoder raftMembershipsEncoder = encoder.raftMembershipsCount(raftMemberships.size());
            for (final RaftMembership raftMembership : raftMemberships)
            {
                raftMembershipsEncoder.next()
                    .partitionId(raftMembership.partitionId())
                    .term(raftMembership.term())
                    .state(raftMembership.state())
                    .putTopicName(raftMembership.topicNameBuffer(), 0, raftMembership.topicNameLength());
            }

        }
    }

}
