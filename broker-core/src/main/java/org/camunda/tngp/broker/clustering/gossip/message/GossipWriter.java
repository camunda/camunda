package org.camunda.tngp.broker.clustering.gossip.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;
import static org.camunda.tngp.management.gossip.GossipEncoder.PeersEncoder.*;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.data.Heartbeat;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.management.gossip.GossipEncoder;
import org.camunda.tngp.management.gossip.GossipEncoder.PeersEncoder;
import org.camunda.tngp.management.gossip.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

public class GossipWriter implements BufferWriter
{
    protected final PeerList values;

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final GossipEncoder bodyEncoder = new GossipEncoder();

    public GossipWriter(final PeerList values)
    {
        this.values = values;
    }

    @Override
    public int getLength()
    {
        final int size = values.size();

        int length = headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();

        length += sbeHeaderSize() + (sbeBlockLength() + hostHeaderLength()) * size;

        final Iterator<Peer> iterator = values.iterator();
        while (iterator.hasNext())
        {
            final Peer current = iterator.next();
            final Endpoint endpoint = current.endpoint();

            length += endpoint.hostLength();
        }

        return length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        final PeersEncoder encoder = bodyEncoder.wrap(buffer, offset)
                .peersCount(values.size());

        final Iterator<Peer> iterator = values.iterator();
        while (iterator.hasNext())
        {
            final Peer current = iterator.next();

            final Endpoint endpoint = current.endpoint();
            final Heartbeat heartbeat = current.heartbeat();

            final DirectBuffer host = endpoint.getBuffer();
            final int hostLength = endpoint.hostLength();

            encoder.next()
                .port(endpoint.port())
                .state(current.state())
                .generation(heartbeat.generation())
                .version(heartbeat.version())
                .putHost(host, hostOffset(0), hostLength);
        }
    }

}
