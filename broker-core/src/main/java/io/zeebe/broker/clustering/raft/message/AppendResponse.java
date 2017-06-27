package io.zeebe.broker.clustering.raft.message;

import static io.zeebe.clustering.raft.AppendResponseEncoder.entryPositionNullValue;
import static io.zeebe.clustering.raft.AppendResponseEncoder.hostHeaderLength;
import static io.zeebe.clustering.raft.AppendResponseEncoder.partitionIdNullValue;
import static io.zeebe.clustering.raft.AppendResponseEncoder.termNullValue;
import static io.zeebe.clustering.raft.AppendResponseEncoder.topicNameHeaderLength;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.clustering.raft.AppendResponseDecoder;
import io.zeebe.clustering.raft.AppendResponseEncoder;
import io.zeebe.clustering.raft.BooleanType;
import io.zeebe.clustering.raft.MessageHeaderDecoder;
import io.zeebe.clustering.raft.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class AppendResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final AppendResponseDecoder bodyDecoder = new AppendResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final AppendResponseEncoder bodyEncoder = new AppendResponseEncoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();
    protected int term = termNullValue();

    protected boolean succeeded;
    protected long entryPosition = entryPositionNullValue();

    protected final Member member = new Member();
    protected boolean isMemberAvailable = false;

    public DirectBuffer topicName()
    {
        return topicName;
    }

    public AppendResponse topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public int partitionId()
    {
        return partitionId;
    }

    public AppendResponse partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public int term()
    {
        return term;
    }

    public AppendResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    public boolean succeeded()
    {
        return succeeded;
    }

    public AppendResponse succeeded(final boolean succeeded)
    {
        this.succeeded = succeeded;
        return this;
    }

    public long entryPosition()
    {
        return entryPosition;
    }

    public AppendResponse entryPosition(final long entryPosition)
    {
        this.entryPosition = entryPosition;
        return this;
    }

    public Member member()
    {
        return isMemberAvailable ? member : null;
    }

    public AppendResponse member(final Member member)
    {
        isMemberAvailable = false;
        this.member.endpoint().reset();
        if (member != null)
        {
            this.member.endpoint().wrap(member.endpoint());
            isMemberAvailable = true;
        }

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
        succeeded = bodyDecoder.succeeded() == BooleanType.TRUE;
        entryPosition = bodyDecoder.entryPosition();

        final int topicNameLength = bodyDecoder.topicNameLength();
        final int topicNameOffset = bodyDecoder.limit() + topicNameHeaderLength();
        topicName.wrap(buffer, topicNameOffset, topicNameLength);

        bodyDecoder.limit(topicNameOffset + topicNameLength);

        isMemberAvailable = false;
        member.endpoint().reset();

        final int hostLength = bodyDecoder.hostLength();
        if (hostLength > 0)
        {
            member.endpoint().port(bodyDecoder.port());

            final MutableDirectBuffer endpointBuffer = member.endpoint().getHostBuffer();
            member.endpoint().hostLength(hostLength);
            bodyDecoder.getHost(endpointBuffer, 0, hostLength);

            isMemberAvailable = true;
        }
        else
        {
            // skip host header in decoder
            bodyDecoder.limit(bodyDecoder.limit() + hostHeaderLength());
        }


        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    @Override
    public int getLength()
    {
        int size = headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                topicNameHeaderLength() +
                topicName.capacity() +
                hostHeaderLength();

        if (isMemberAvailable)
        {
            size += member.endpoint().hostLength();
        }

        return size;
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
            .succeeded(succeeded ? BooleanType.TRUE : BooleanType.FALSE)
            .entryPosition(entryPosition)
            .port(member.endpoint().port())
            .putTopicName(topicName, 0, topicName.capacity())
            .putHost(member.endpoint().getHostBuffer(), 0, member.endpoint().hostLength());

    }

    public void reset()
    {
        topicName.wrap(0, 0);
        partitionId = partitionIdNullValue();
        term = termNullValue();
        succeeded = false;
        entryPosition = entryPositionNullValue();
        member.endpoint().reset();
        isMemberAvailable = false;
    }

}
