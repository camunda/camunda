package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;
import static org.camunda.tngp.clustering.raft.VoteRequestDecoder.*;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.clustering.raft.RaftHeaderDecoder;
import org.camunda.tngp.clustering.raft.VoteRequestDecoder;
import org.camunda.tngp.clustering.raft.VoteRequestEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class VoteRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final VoteRequestDecoder bodyDecoder = new VoteRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final VoteRequestEncoder bodyEncoder = new VoteRequestEncoder();

    protected int log;
    protected int term;

    protected long lastEntryPosition;
    protected int lastEntryTerm;

    protected Endpoint candidate = new Endpoint();

    public int log()
    {
        return log;
    }

    public VoteRequest log(final int log)
    {
        this.log = log;
        return this;
    }

    public int term()
    {
        return term;
    }

    public VoteRequest term(final int term)
    {
        this.term = term;
        return this;
    }

    public long lastEntryPosition()
    {
        return lastEntryPosition;
    }

    public VoteRequest lastEntryPosition(final long lastEntryPosition)
    {
        this.lastEntryPosition = lastEntryPosition;
        return this;
    }

    public int lastEntryTerm()
    {
        return lastEntryTerm;
    }

    public VoteRequest lastEntryTerm(final int lastEntryTerm)
    {
        this.lastEntryTerm = lastEntryTerm;
        return this;
    }

    public Endpoint candidate()
    {
        return candidate;
    }

    public VoteRequest candidate(final Endpoint candidate)
    {
        this.candidate = candidate;
        return this;
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        final RaftHeaderDecoder raftHeaderDecoder = bodyDecoder.header();

        log = raftHeaderDecoder.log();
        term = raftHeaderDecoder.term();

        lastEntryPosition = bodyDecoder.lastEntryPosition();
        lastEntryTerm = bodyDecoder.lastEntryTerm();

        candidate.port(bodyDecoder.port());
        final int hostLength = bodyDecoder.hostLength();
        final MutableDirectBuffer endpointBuffer = (MutableDirectBuffer) candidate.getBuffer();

        endpointBuffer.putInt(hostLengthOffset(0), hostLength);
        bodyDecoder.getHost(endpointBuffer, hostOffset(0), hostLength);
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                hostHeaderLength() +
                candidate.hostLength();
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

        bodyEncoder.wrap(buffer, offset);

        bodyEncoder.header()
            .log(log)
            .term(term);

        bodyEncoder
            .lastEntryPosition(lastEntryPosition)
            .lastEntryTerm(lastEntryTerm);

        bodyEncoder.port(candidate.port());
        bodyEncoder.putHost(candidate.getBuffer(), hostOffset(0), candidate.hostLength());
    }

    public void reset()
    {
        log = -1;
        term = -1;
        lastEntryPosition = -1L;
        lastEntryTerm = -1;
        candidate.clear();
    }

}
