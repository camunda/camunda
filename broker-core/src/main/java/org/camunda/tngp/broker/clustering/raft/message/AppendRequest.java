package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.clustering.raft.AppendRequestEncoder.commitPositionNullValue;
import static org.camunda.tngp.clustering.raft.AppendRequestEncoder.dataHeaderLength;
import static org.camunda.tngp.clustering.raft.AppendRequestEncoder.hostHeaderLength;
import static org.camunda.tngp.clustering.raft.AppendRequestEncoder.partitionIdNullValue;
import static org.camunda.tngp.clustering.raft.AppendRequestEncoder.previousEntryPositionNullValue;
import static org.camunda.tngp.clustering.raft.AppendRequestEncoder.previousEntryTermNullValue;
import static org.camunda.tngp.clustering.raft.AppendRequestEncoder.termNullValue;
import static org.camunda.tngp.clustering.raft.AppendRequestEncoder.topicNameHeaderLength;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.clustering.raft.AppendRequestDecoder;
import org.camunda.tngp.clustering.raft.AppendRequestEncoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.logstreams.impl.LoggedEventImpl;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;


public class AppendRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final AppendRequestDecoder bodyDecoder = new AppendRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final AppendRequestEncoder bodyEncoder = new AppendRequestEncoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();
    protected int term = termNullValue();

    protected long previousEntryPosition = previousEntryPositionNullValue();
    protected int previousEntryTerm = previousEntryTermNullValue();

    protected long commitPosition = commitPositionNullValue();

    protected final Member leader = new Member();
    protected final LoggedEventImpl readableEntry = new LoggedEventImpl();
    protected LoggedEventImpl writeableEntry;

    protected boolean isLeaderAvailable = false;
    protected boolean isReadableEntryAvailable = false;

    public DirectBuffer topicName()
    {
        return topicName;
    }

    public AppendRequest topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public int partitionId()
    {
        return partitionId;
    }

    public AppendRequest partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public int term()
    {
        return term;
    }

    public AppendRequest term(final int term)
    {
        this.term = term;
        return this;
    }

    public long previousEntryPosition()
    {
        return previousEntryPosition;
    }

    public AppendRequest previousEntryPosition(final long previousEntryPosition)
    {
        this.previousEntryPosition = previousEntryPosition;
        return this;
    }

    public int previousEntryTerm()
    {
        return previousEntryTerm;
    }

    public AppendRequest previousEntryTerm(final int previousEntryTerm)
    {
        this.previousEntryTerm = previousEntryTerm;
        return this;
    }

    public long commitPosition()
    {
        return commitPosition;
    }

    public AppendRequest commitPosition(final long commitPosition)
    {
        this.commitPosition = commitPosition;
        return this;
    }

    public Member leader()
    {
        return isLeaderAvailable ? leader : null;
    }

    public AppendRequest leader(final Member leader)
    {
        isLeaderAvailable = false;
        this.leader.endpoint().reset();

        if (leader != null)
        {
            this.leader.endpoint().wrap(leader.endpoint());
            isLeaderAvailable = true;
        }

        return this;
    }

    public LoggedEventImpl entry()
    {
        return isReadableEntryAvailable ? readableEntry : null;
    }

    public AppendRequest entry(final LoggedEvent entry)
    {
        this.writeableEntry = (LoggedEventImpl) entry;
        return this;
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        partitionId = bodyDecoder.partitionId();
        term = bodyDecoder.term();

        previousEntryPosition = bodyDecoder.previousEntryPosition();
        previousEntryTerm = bodyDecoder.previousEntryTerm();
        commitPosition = bodyDecoder.commitPosition();

        final int topicNameLength = bodyDecoder.topicNameLength();
        final int topicNameOffset = bodyDecoder.limit() + topicNameHeaderLength();
        topicName.wrap(buffer, topicNameOffset, topicNameLength);

        bodyDecoder.limit(topicNameOffset + topicNameLength);

        isLeaderAvailable = false;
        leader.endpoint().reset();

        final int hostLength = bodyDecoder.hostLength();
        if (hostLength > 0)
        {
            leader.endpoint().port(bodyDecoder.port());

            final MutableDirectBuffer endpointBuffer = leader.endpoint().getHostBuffer();
            leader.endpoint().hostLength(hostLength);
            bodyDecoder.getHost(endpointBuffer, 0, hostLength);

            isLeaderAvailable = true;
        }

        offset = bodyDecoder.limit() + AppendRequestDecoder.dataHeaderLength();

        isReadableEntryAvailable = false;
        if (bodyDecoder.dataLength() > 0)
        {
            readableEntry.wrap(buffer, offset);
            isReadableEntryAvailable = true;
        }
    }

    @Override
    public int getLength()
    {
        int size = headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                dataHeaderLength() +
                topicNameHeaderLength() +
                topicName.capacity() +
                hostHeaderLength();

        if (isLeaderAvailable)
        {
            size += leader.endpoint().hostLength();
        }

        if (writeableEntry != null)
        {
            size += writeableEntry.getFragmentLength();
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
            .previousEntryPosition(previousEntryPosition)
            .previousEntryTerm(previousEntryTerm)
            .commitPosition(commitPosition)
            .port(leader.endpoint().port())
            .putTopicName(topicName, 0, topicName.capacity())
            .putHost(leader.endpoint().getHostBuffer(), 0, leader.endpoint().hostLength());

        if (writeableEntry != null)
        {
            final DirectBuffer valueBuffer = writeableEntry.getValueBuffer();
            final int fragementOffset = writeableEntry.getFragementOffset();
            final int fragmentLength = writeableEntry.getFragmentLength();
            bodyEncoder.putData(valueBuffer, fragementOffset, fragmentLength);
        }
    }

    public void reset()
    {
        topicName.wrap(0, 0);
        partitionId = partitionIdNullValue();
        term = termNullValue();
        previousEntryPosition = previousEntryPositionNullValue();
        previousEntryTerm = previousEntryTermNullValue();
        commitPosition = commitPositionNullValue();
        leader.endpoint().reset();
        isLeaderAvailable = false;
        isReadableEntryAvailable = false;
        writeableEntry = null;
    }

}
