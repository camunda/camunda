package io.zeebe.broker.clustering.raft.message;

import static io.zeebe.clustering.raft.ConfigureResponseEncoder.termNullValue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.clustering.raft.ConfigureResponseDecoder;
import io.zeebe.clustering.raft.ConfigureResponseEncoder;
import io.zeebe.clustering.raft.MessageHeaderDecoder;
import io.zeebe.clustering.raft.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class ConfigureResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ConfigureResponseEncoder bodyEncoder = new ConfigureResponseEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ConfigureResponseDecoder bodyDecoder = new ConfigureResponseDecoder();

    private int term = termNullValue();

    public int term()
    {
        return term;
    }

    public ConfigureResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength();
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

        bodyEncoder.wrap(buffer, offset)
            .term(term);
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        term = bodyDecoder.term();

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    public void reset()
    {
        term = termNullValue();
    }

}
