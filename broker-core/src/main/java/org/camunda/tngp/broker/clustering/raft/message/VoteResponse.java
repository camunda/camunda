package org.camunda.tngp.broker.clustering.raft.message;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.clustering.raft.BooleanType;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.clustering.raft.RaftHeaderDecoder;
import org.camunda.tngp.clustering.raft.VoteResponseDecoder;
import org.camunda.tngp.clustering.raft.VoteResponseEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class VoteResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final VoteResponseDecoder bodyDecoder = new VoteResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final VoteResponseEncoder bodyEncoder = new VoteResponseEncoder();

    protected int log;
    protected int term;

    protected boolean granted;

    public int log()
    {
        return log;
    }

    public VoteResponse log(final int log)
    {
        this.log = log;
        return this;
    }

    public int term()
    {
        return term;
    }

    public VoteResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    public boolean granted()
    {
        return granted;
    }

    public VoteResponse granted(final boolean granted)
    {
        this.granted = granted;
        return this;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        final RaftHeaderDecoder raftHeaderDecoder = bodyDecoder.header();

        log = raftHeaderDecoder.log();
        term = raftHeaderDecoder.term();

        granted = bodyDecoder.granted() == BooleanType.TRUE;
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

        bodyEncoder.wrap(buffer, offset);

        bodyEncoder.header()
            .log(log)
            .term(term);

        bodyEncoder.granted(granted ? BooleanType.TRUE : BooleanType.FALSE);
    }

    public void reset()
    {
        log = -1;
        term = -1;
        granted = false;
    }

}
