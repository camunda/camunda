package io.zeebe.broker.clustering.raft.message;

import static io.zeebe.clustering.raft.PollResponseEncoder.termNullValue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.clustering.raft.BooleanType;
import io.zeebe.clustering.raft.MessageHeaderDecoder;
import io.zeebe.clustering.raft.MessageHeaderEncoder;
import io.zeebe.clustering.raft.PollResponseDecoder;
import io.zeebe.clustering.raft.PollResponseEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class PollResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final PollResponseDecoder bodyDecoder = new PollResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final PollResponseEncoder bodyEncoder = new PollResponseEncoder();

    protected int term = termNullValue();

    protected boolean granted;

    public int term()
    {
        return term;
    }

    public PollResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    public boolean granted()
    {
        return granted;
    }

    public PollResponse granted(final boolean granted)
    {
        this.granted = granted;
        return this;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        term = bodyDecoder.term();
        granted = bodyDecoder.granted() == BooleanType.TRUE;

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
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
            .term(term)
            .granted(granted ? BooleanType.TRUE : BooleanType.FALSE);
    }

    public void reset()
    {
        term = termNullValue();
        granted = false;
    }
}
