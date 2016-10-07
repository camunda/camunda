package org.camunda.tngp.broker.clustering.management.message;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.management.cluster.BooleanType;
import org.camunda.tngp.management.cluster.InvitationResponseDecoder;
import org.camunda.tngp.management.cluster.InvitationResponseEncoder;
import org.camunda.tngp.management.cluster.MessageHeaderDecoder;
import org.camunda.tngp.management.cluster.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class InvitationResponse implements BufferWriter, BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final InvitationResponseDecoder bodyDecoder = new InvitationResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final InvitationResponseEncoder bodyEncoder = new InvitationResponseEncoder();

    protected boolean acknowledged;

    public boolean acknowledged()
    {
        return acknowledged;
    }

    public InvitationResponse acknowledged(final boolean acknowledged)
    {
        this.acknowledged = acknowledged;
        return this;
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        acknowledged = bodyDecoder.acknowledged() == BooleanType.TRUE;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
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

        bodyEncoder.wrap(buffer, offset)
            .acknowledged(acknowledged ? BooleanType.TRUE : BooleanType.FALSE);
    }
}
