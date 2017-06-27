package io.zeebe.broker.clustering.management.message;

import static io.zeebe.clustering.management.InvitationRequestEncoder.termNullValue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.clustering.management.InvitationResponseDecoder;
import io.zeebe.clustering.management.InvitationResponseEncoder;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class InvitationResponse implements BufferWriter, BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final InvitationResponseDecoder bodyDecoder = new InvitationResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final InvitationResponseEncoder bodyEncoder = new InvitationResponseEncoder();

    protected int term = termNullValue();

    public int term()
    {
        return term;
    }

    public InvitationResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        term = bodyDecoder.term();

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
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
            .term(term);
    }

    public void reset()
    {
        term = termNullValue();
    }
}
