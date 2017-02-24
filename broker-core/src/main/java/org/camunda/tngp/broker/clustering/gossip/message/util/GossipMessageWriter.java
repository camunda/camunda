package org.camunda.tngp.broker.clustering.gossip.message.util;

import static org.camunda.tngp.clustering.gossip.GossipDecoder.PeersDecoder.*;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.gossip.data.Heartbeat;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.clustering.gossip.GossipEncoder;
import org.camunda.tngp.clustering.gossip.GossipEncoder.PeersEncoder;
import org.camunda.tngp.clustering.gossip.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

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

        int length = bodyEncoder.sbeBlockLength() +
                sbeHeaderSize() +
                (sbeBlockLength() + clientHostHeaderLength() + managementHostHeaderLength() + replicationHostHeaderLength()) * size;

        final Iterator<Peer> iterator = peers.iterator();
        while (iterator.hasNext())
        {
            final Peer current = iterator.next();
            length += current.clientEndpoint().hostLength();
            length += current.managementEndpoint().hostLength();
            length += current.replicationEndpoint().hostLength();
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

            final Endpoint clientEndpoint = current.clientEndpoint();
            final Endpoint managementEndpoint = current.managementEndpoint();
            final Endpoint replicationEndpoint = current.replicationEndpoint();

            final DirectBuffer clientHostBuffer = clientEndpoint.getHostBuffer();
            final int clientHostLength = clientEndpoint.hostLength();
            final int clientPort = clientEndpoint.port();

            final DirectBuffer managementHostBuffer = managementEndpoint.getHostBuffer();
            final int managementHostLength = managementEndpoint.hostLength();
            final int managementPort = managementEndpoint.port();

            final DirectBuffer replicationHostBuffer = replicationEndpoint.getHostBuffer();
            final int replicationHostLength = replicationEndpoint.hostLength();
            final int replicationPort = replicationEndpoint.port();

            encoder.next()
                .state(current.state())
                .generation(heartbeat.generation())
                .version(heartbeat.version())
                .clientPort(clientPort)
                .managementPort(managementPort)
                .replicationPort(replicationPort)
                .putClientHost(clientHostBuffer, 0, clientHostLength)
                .putManagementHost(managementHostBuffer, 0, managementHostLength)
                .putReplicationHost(replicationHostBuffer, 0, replicationHostLength);
        }
    }

}
