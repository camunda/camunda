package org.camunda.tngp.broker.clustering.raft.message;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.clustering.raft.AppendResponseDecoder;
import org.camunda.tngp.clustering.raft.AppendResponseEncoder;
import org.camunda.tngp.clustering.raft.BooleanType;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class AppendResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final AppendResponseDecoder bodyDecoder = new AppendResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final AppendResponseEncoder bodyEncoder = new AppendResponseEncoder();

    protected int id;
    protected int term;

    protected boolean succeeded;
    protected long entryPosition;

    protected final Member member = new Member();
    protected boolean isMemberAvailable = false;

    public int id()
    {
        return id;
    }

    public AppendResponse id(final int id)
    {
        this.id = id;
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
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        id = bodyDecoder.id();
        term = bodyDecoder.term();
        succeeded = bodyDecoder.succeeded() == BooleanType.TRUE;
        entryPosition = bodyDecoder.entryPosition();

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
    }

    @Override
    public int getLength()
    {
        int size = headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                AppendResponseEncoder.hostHeaderLength();

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
            .id(id)
            .term(term)
            .succeeded(succeeded ? BooleanType.TRUE : BooleanType.FALSE)
            .entryPosition(entryPosition)
            .port(member.endpoint().port())
            .putHost(member.endpoint().getHostBuffer(), 0, member.endpoint().hostLength());

    }

    public void reset()
    {
        id = -1;
        term = -1;
        succeeded = false;
        entryPosition = -1L;
        member.endpoint().reset();
        isMemberAvailable = false;
    }

}
