package org.camunda.tngp.broker.clustering.gossip.message;

import static org.camunda.tngp.broker.clustering.util.Endpoint.*;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.management.gossip.GossipDecoder;
import org.camunda.tngp.management.gossip.GossipDecoder.PeersDecoder;
import org.camunda.tngp.management.gossip.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

public class GossipReader implements BufferReader, Iterator<Peer>
{
    protected Iterator<PeersDecoder> iterator;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final GossipDecoder bodyDecoder = new GossipDecoder();

    protected final Peer currentPeer = new Peer();
    protected final byte[] currentHostBuffer = new byte[MAX_HOST_LENGTH];

    @Override
    public void wrap(final DirectBuffer values, int offset, final int length)
    {
        headerDecoder.wrap(values, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(values, offset, headerDecoder.blockLength(), headerDecoder.version());
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

        currentPeer.heartbeat()
            .generation(decoder.generation())
            .version(decoder.version());

        final int hostLength = decoder.hostLength();
        decoder.getHost(currentHostBuffer, 0, hostLength);

        currentPeer.endpoint()
            .port(decoder.port())
            .host(currentHostBuffer, 0, hostLength);

        currentPeer.state(decoder.state())
            .changeStateTime(-1L)
            .localPeer(false); // set always to false, even if that passed peer is this local peer

        return currentPeer;
    }

}
