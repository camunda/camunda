package io.zeebe.broker.clustering.raft.message;

import static io.zeebe.clustering.raft.VoteRequestEncoder.hostHeaderLength;
import static io.zeebe.clustering.raft.VoteRequestEncoder.lastEntryPositionNullValue;
import static io.zeebe.clustering.raft.VoteRequestEncoder.lastEntryTermNullValue;
import static io.zeebe.clustering.raft.VoteRequestEncoder.partitionIdNullValue;
import static io.zeebe.clustering.raft.VoteRequestEncoder.termNullValue;
import static io.zeebe.clustering.raft.VoteRequestEncoder.topicNameHeaderLength;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.clustering.raft.MessageHeaderDecoder;
import io.zeebe.clustering.raft.MessageHeaderEncoder;
import io.zeebe.clustering.raft.VoteRequestDecoder;
import io.zeebe.clustering.raft.VoteRequestEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class VoteRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final VoteRequestDecoder bodyDecoder = new VoteRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final VoteRequestEncoder bodyEncoder = new VoteRequestEncoder();

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

    public VoteRequest topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public int partitionId()
    {
        return partitionId;
    }

    public VoteRequest partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
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

    public Member candidate()
    {
        return candidate;
    }

    public VoteRequest candidate(final Member candidate)
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

        final int topicNameLength = bodyDecoder.topicNameLength();
        final int topicNameOffset = bodyDecoder.limit() + topicNameHeaderLength();
        topicName.wrap(buffer, topicNameOffset, topicNameLength);

        bodyDecoder.limit(topicNameOffset + topicNameLength);

        candidate.endpoint().port(bodyDecoder.port());

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
            .lastEntryTerm(lastEntryTerm);

        bodyEncoder.port(candidate.endpoint().port());
        bodyEncoder.putTopicName(topicName, 0, topicName.capacity());
        bodyEncoder.putHost(candidate.endpoint().getHostBuffer(), 0, candidate.endpoint().hostLength());
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
