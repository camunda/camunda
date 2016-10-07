package org.camunda.tngp.broker.clustering.gossip.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.management.gossip.MessageHeaderEncoder;
import org.camunda.tngp.management.gossip.ProbeEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ProbeWriter implements BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ProbeEncoder bodyEncoder = new ProbeEncoder();

    protected Peer member;

    public ProbeWriter member(final Peer member)
    {
        this.member = member;
        return this;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                ProbeEncoder.hostHeaderLength() +
                member.endpoint().hostLength();
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        final Endpoint endpoint = member.endpoint();

        bodyEncoder.wrap(buffer, offset)
            .port(endpoint.port())
            .putHost(endpoint.getBuffer(), hostOffset(0), endpoint.hostLength());
    }

}
