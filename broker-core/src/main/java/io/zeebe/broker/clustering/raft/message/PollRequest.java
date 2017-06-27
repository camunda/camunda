package io.zeebe.broker.clustering.raft.message;

import static io.zeebe.clustering.raft.PollRequestEncoder.hostHeaderLength;
import static io.zeebe.clustering.raft.PollRequestEncoder.lastEntryPositionNullValue;
import static io.zeebe.clustering.raft.PollRequestEncoder.lastEntryTermNullValue;
import static io.zeebe.clustering.raft.PollRequestEncoder.partitionIdNullValue;
import static io.zeebe.clustering.raft.PollRequestEncoder.termNullValue;
import static io.zeebe.clustering.raft.PollRequestEncoder.topicNameHeaderLength;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.clustering.raft.MessageHeaderDecoder;
import io.zeebe.clustering.raft.MessageHeaderEncoder;
import io.zeebe.clustering.raft.PollRequestDecoder;
import io.zeebe.clustering.raft.PollRequestEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class PollRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final PollRequestDecoder bodyDecoder = new PollRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final PollRequestEncoder bodyEncoder = new PollRequestEncoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();
    protected int term = termNullValue();

    protected long lastEntryPosition = lastEntryPositionNullValue();
    protected int lastEntryTerm = lastEntryTermNullValue();

    protected final Member candidate = new Member();

    public DirectBuffer topicName()
    {
        return topicName;
    }

    public PollRequest topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public int partitionId()
    {
        return partitionId;
    }

    public PollRequest partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
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
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        partitionId = bodyDecoder.partitionId();
        term = bodyDecoder.term();
        lastEntryPosition = bodyDecoder.lastEntryPosition();
        lastEntryTerm = bodyDecoder.lastEntryTerm();

        candidate.endpoint().port(bodyDecoder.port());

        final int topicNameLength = bodyDecoder.topicNameLength();
        final int topicNameOffset = bodyDecoder.limit() + topicNameHeaderLength();
        topicName.wrap(buffer, topicNameOffset, topicNameLength);

        bodyDecoder.limit(topicNameOffset + topicNameLength);

        final int hostLength = bodyDecoder.hostLength();
        final MutableDirectBuffer endpointBuffer = candidate.endpoint().getHostBuffer();
        candidate.endpoint().hostLength(hostLength);
        bodyDecoder.getHost(endpointBuffer, 0, hostLength);

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                topicNameHeaderLength() +
                topicName.capacity() +
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
            .partitionId(partitionId)
            .term(term)
            .lastEntryPosition(lastEntryPosition)
            .lastEntryTerm(lastEntryTerm)
            .port(candidate.endpoint().port())
            .putTopicName(topicName, 0, topicName.capacity())
            .putHost(candidate.endpoint().getHostBuffer(), 0, candidate.endpoint().hostLength());
    }

    public void reset()
    {
        topicName.wrap(0, 0);
        partitionId = partitionIdNullValue();
        term = termNullValue();
        lastEntryPosition = lastEntryPositionNullValue();
        lastEntryTerm = lastEntryTermNullValue();
        candidate.endpoint().reset();
    }
}
