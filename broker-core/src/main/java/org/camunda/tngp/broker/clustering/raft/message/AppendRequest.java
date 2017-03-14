package org.camunda.tngp.broker.clustering.raft.message;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.clustering.raft.AppendRequestDecoder;
import org.camunda.tngp.clustering.raft.AppendRequestEncoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.log.LoggedEventImpl;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

import static org.camunda.tngp.clustering.raft.AppendRequestDecoder.dataHeaderLength;
import static org.camunda.tngp.clustering.raft.AppendRequestDecoder.hostHeaderLength;

public class AppendRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final AppendRequestDecoder bodyDecoder = new AppendRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final AppendRequestEncoder bodyEncoder = new AppendRequestEncoder();

    protected int id;
    protected int term;

    protected long previousEntryPosition;
    protected int previousEntryTerm;

    protected long commitPosition;

    protected final Member leader = new Member();
    protected final LoggedEventImpl readableEntry = new LoggedEventImpl();
    protected LoggedEventImpl writeableEntry;

    protected boolean isLeaderAvailable = false;
    protected boolean isReadableEntryAvailable = false;

    public int id()
    {
        return id;
    }

    public AppendRequest log(final int id)
    {
        this.id = id;
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

        id = bodyDecoder.id();
        term = bodyDecoder.term();

        previousEntryPosition = bodyDecoder.previousEntryPosition();
        previousEntryTerm = bodyDecoder.previousEntryTerm();
        commitPosition = bodyDecoder.commitPosition();

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

        offset += bodyDecoder.sbeBlockLength();
        offset += AppendRequestDecoder.hostHeaderLength();
        offset += hostLength;
        offset += AppendRequestDecoder.dataHeaderLength();

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
            .id(id)
            .term(term)
            .previousEntryPosition(previousEntryPosition)
            .previousEntryTerm(previousEntryTerm)
            .commitPosition(commitPosition)
            .port(leader.endpoint().port())
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
        id = -1;
        term = -1;
        previousEntryPosition = -1L;
        previousEntryTerm = -1;
        leader.endpoint().reset();
        isLeaderAvailable = false;
        isReadableEntryAvailable = false;
        writeableEntry = null;
    }

}
