package org.camunda.tngp.broker.clustering.gossip.message.util;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.clustering.gossip.GossipDecoder;
import org.camunda.tngp.clustering.gossip.GossipDecoder.PeersDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

public class GossipMessageReader implements BufferReader, Iterator<Peer>
{
    private Iterator<PeersDecoder> iterator;

    private final GossipDecoder bodyDecoder = new GossipDecoder();

    private final Peer currentPeer = new Peer();

    @Override
    public void wrap(final DirectBuffer values, int offset, final int length)
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

        final int clientPort = decoder.clientPort();
        final int managementPort = decoder.managementPort();
        final int replicationPort = decoder.replicationPort();

        final Endpoint clientEndpoint = currentPeer.clientEndpoint();
        final MutableDirectBuffer clientHostBuffer = clientEndpoint.getHostBuffer();
        final int clientHostLength = decoder.clientHostLength();
        clientEndpoint.port(clientPort);
        clientEndpoint.hostLength(clientHostLength);
        decoder.getClientHost(clientHostBuffer, 0, clientHostLength);

        final Endpoint managementEndpoint = currentPeer.managementEndpoint();
        final MutableDirectBuffer managementHostBuffer = managementEndpoint.getHostBuffer();
        final int managementHostLength = decoder.managementHostLength();
        managementEndpoint.port(managementPort);
        managementEndpoint.hostLength(managementHostLength);
        decoder.getClientHost(managementHostBuffer, 0, managementHostLength);

        final Endpoint replicationEndpoint = currentPeer.replicationEndpoint();
        final MutableDirectBuffer replicationHostBuffer = replicationEndpoint.getHostBuffer();
        final int replicationHostLength = decoder.managementHostLength();
        replicationEndpoint.port(replicationPort);
        replicationEndpoint.hostLength(replicationHostLength);
        decoder.getClientHost(replicationHostBuffer, 0, replicationHostLength);

        currentPeer.state(decoder.state())
            .changeStateTime(-1L);

        return currentPeer;
    }

}
