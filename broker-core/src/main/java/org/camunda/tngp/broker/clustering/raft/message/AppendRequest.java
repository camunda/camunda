package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;
import static org.camunda.tngp.clustering.raft.AppendRequestDecoder.*;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.clustering.raft.AppendRequestDecoder;
import org.camunda.tngp.clustering.raft.AppendRequestEncoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.clustering.raft.RaftHeaderDecoder;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader.LoggedEventImpl;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class AppendRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final AppendRequestDecoder bodyDecoder = new AppendRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final AppendRequestEncoder bodyEncoder = new AppendRequestEncoder();

    protected int term;
    protected int log;
    protected int index;

    protected long previousEntryPosition;
    protected int previousEntryTerm;

    protected long commitPosition;

    protected final Endpoint leader = new Endpoint();
    protected final LoggedEventImpl readableEntry = new LoggedEventImpl();
    protected LoggedEventImpl writeableEntry;

    protected boolean isLeaderAvailable = false;
    protected boolean isReadableEntryAvailable = false;

    public int log()
    {
        return log;
    }

    public AppendRequest log(final int log)
    {
        this.log = log;
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

    public int index()
    {
        return index;
    }

    public AppendRequest index(final int index)
    {
        this.index = index;
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

    public Endpoint leader()
    {
        return isLeaderAvailable ? leader : null;
    }

    public AppendRequest leader(final Endpoint leader)
    {
        isLeaderAvailable = false;
        if (leader != null)
        {
            this.leader.wrap(leader);
            isLeaderAvailable = true;
        }
        else
        {
            this.leader.clear();
        }

        return this;
    }

    public LoggedEvent entry()
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

        final RaftHeaderDecoder raftHeaderDecoder = bodyDecoder.header();
        log = raftHeaderDecoder.log();
        term = raftHeaderDecoder.term();

        previousEntryPosition = bodyDecoder.previousEntryPosition();
        previousEntryTerm = bodyDecoder.previousEntryTerm();
        commitPosition = bodyDecoder.commitPosition();
        index = bodyDecoder.index();

        isLeaderAvailable = false;
        leader.clear();

        final int hostLength = bodyDecoder.hostLength();
        if (hostLength > 0)
        {
            leader.port(bodyDecoder.port());
            final MutableDirectBuffer endpointBuffer = (MutableDirectBuffer) leader.getBuffer();

            endpointBuffer.putInt(hostLengthOffset(0), hostLength);
            bodyDecoder.getHost(endpointBuffer, hostOffset(0), hostLength);

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
            size += leader.hostLength();
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

        bodyEncoder.wrap(buffer, offset);

        bodyEncoder.header()
            .log(log)
            .term(term);

        bodyEncoder
            .previousEntryPosition(previousEntryPosition)
            .previousEntryTerm(previousEntryTerm)
            .commitPosition(commitPosition)
            .index(index);

        bodyEncoder.port(leader.port());
        bodyEncoder.putHost(leader.getBuffer(), hostOffset(0), leader.hostLength());

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
        log = -1;
        term = -1;
        previousEntryPosition = -1L;
        previousEntryTerm = -1;
        leader.clear();
        isLeaderAvailable = false;
        isReadableEntryAvailable = false;
        writeableEntry = null;
    }

}
