package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.clustering.raft.AppendResponseDecoder;
import org.camunda.tngp.clustering.raft.AppendResponseEncoder;
import org.camunda.tngp.clustering.raft.BooleanType;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.clustering.raft.RaftHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class AppendResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final AppendResponseDecoder bodyDecoder = new AppendResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final AppendResponseEncoder bodyEncoder = new AppendResponseEncoder();

    protected int log;
    protected int term;

    protected boolean succeeded;
    protected long entryPosition;

    protected Endpoint member = new Endpoint();
    protected boolean isMemberAvailable = false;

    public int log()
    {
        return log;
    }

    public AppendResponse log(final int log)
    {
        this.log = log;
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

    public Endpoint member()
    {
        return isMemberAvailable ? member : null;
    }

    public AppendResponse member(final Endpoint member)
    {
        isMemberAvailable = false;
        if (member != null)
        {
            this.member.wrap(member);
            isMemberAvailable = true;
        }
        else
        {
            this.member.clear();
        }

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

        succeeded = bodyDecoder.succeeded() == BooleanType.TRUE;
        entryPosition = bodyDecoder.entryPosition();

        isMemberAvailable = false;
        member.clear();

        final int hostLength = bodyDecoder.hostLength();
        if (hostLength > 0)
        {
            member.port(bodyDecoder.port());
            final MutableDirectBuffer endpointBuffer = (MutableDirectBuffer) member.getBuffer();

            endpointBuffer.putInt(hostLengthOffset(0), hostLength);
            bodyDecoder.getHost(endpointBuffer, hostOffset(0), hostLength);

            isMemberAvailable = true;
        }
    }

    @Override
    public int getLength()
    {
        int size = headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                AppendResponseEncoder.hostHeaderLength();

        if (isMemberAvailable)
        {
            size += member.hostLength();
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
            .succeeded(succeeded ? BooleanType.TRUE : BooleanType.FALSE)
            .entryPosition(entryPosition);

        bodyEncoder.port(member.port());
        bodyEncoder.putHost(member.getBuffer(), hostOffset(0), member.hostLength());

    }

    public void reset()
    {
        log = -1;
        term = -1;
        succeeded = false;
        entryPosition = -1L;
        member.clear();
        isMemberAvailable = false;
    }

}
