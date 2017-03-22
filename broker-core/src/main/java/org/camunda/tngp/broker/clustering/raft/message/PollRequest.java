package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.clustering.raft.PollRequestDecoder.*;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.clustering.raft.PollRequestDecoder;
import org.camunda.tngp.clustering.raft.PollRequestEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class PollRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final PollRequestDecoder bodyDecoder = new PollRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final PollRequestEncoder bodyEncoder = new PollRequestEncoder();

    protected int id;
    protected int term;

    protected long lastEntryPosition;
    protected int lastEntryTerm;

    protected final Member candidate = new Member();

    public int id()
    {
        return id;
    }

    public PollRequest id(final int id)
    {
        this.id = id;
        return this;
    }

    public int term()
    {
        return term;
    }

    public PollRequest term(final int term)
    {
        this.term = term;
        return this;
    }

    public long lastEntryPosition()
    {
        return lastEntryPosition;
    }

    public PollRequest lastEntryPosition(final long lastEntryPosition)
    {
        this.lastEntryPosition = lastEntryPosition;
        return this;
    }

    public int lastEntryTerm()
    {
        return lastEntryTerm;
    }

    public PollRequest lastEntryTerm(final int lastEntryTerm)
    {
        this.lastEntryTerm = lastEntryTerm;
        return this;
    }

    public Member candidate()
    {
        return candidate;
    }

    public PollRequest candidate(final Member candidate)
    {
        this.candidate.endpoint().reset();
        this.candidate.endpoint().wrap(candidate.endpoint());
        return this;
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        id = bodyDecoder.id();
        term = bodyDecoder.term();
        lastEntryPosition = bodyDecoder.lastEntryPosition();
        lastEntryTerm = bodyDecoder.lastEntryTerm();

        candidate.endpoint().port(bodyDecoder.port());

        final int hostLength = bodyDecoder.hostLength();
        final MutableDirectBuffer endpointBuffer = candidate.endpoint().getHostBuffer();
        candidate.endpoint().hostLength(hostLength);
        bodyDecoder.getHost(endpointBuffer, 0, hostLength);
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                hostHeaderLength() +
                candidate.endpoint().hostLength();
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
            .id(id)
            .term(term)
            .lastEntryPosition(lastEntryPosition)
            .lastEntryTerm(lastEntryTerm);

        bodyEncoder.port(candidate.endpoint().port());
        bodyEncoder.putHost(candidate.endpoint().getHostBuffer(), 0, candidate.endpoint().hostLength());
    }

    public void reset()
    {
        id = -1;
        term = -1;
        lastEntryPosition = -1L;
        lastEntryTerm = -1;
        candidate.endpoint().reset();
    }
}
