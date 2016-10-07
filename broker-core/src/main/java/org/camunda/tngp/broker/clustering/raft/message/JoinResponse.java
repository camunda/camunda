package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;
import static org.camunda.tngp.clustering.raft.JoinResponseDecoder.MembersDecoder.*;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.util.MemberTypeResolver;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.clustering.raft.BooleanType;
import org.camunda.tngp.clustering.raft.JoinResponseDecoder;
import org.camunda.tngp.clustering.raft.JoinResponseDecoder.MembersDecoder;
import org.camunda.tngp.clustering.raft.JoinResponseEncoder;
import org.camunda.tngp.clustering.raft.JoinResponseEncoder.MembersEncoder;
import org.camunda.tngp.clustering.raft.MemberType;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.clustering.raft.RaftHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class JoinResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final JoinResponseDecoder bodyDecoder = new JoinResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final JoinResponseEncoder bodyEncoder = new JoinResponseEncoder();

    protected int log = -1;
    protected int term = -1;
    protected long configurationEntryPosition = -1L;
    protected int configurationEntryTerm = -1;
    protected boolean status;
    protected List<Member> members = new CopyOnWriteArrayList<>();

    public boolean status()
    {
        return status;
    }

    public JoinResponse status(final boolean status)
    {
        this.status = status;
        return this;
    }

    public int log()
    {
        return log;
    }

    public JoinResponse log(final int log)
    {
        this.log = log;
        return this;
    }

    public int term()
    {
        return term;
    }

    public JoinResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    public long configurationEntryPosition()
    {
        return configurationEntryPosition;
    }

    public JoinResponse configurationEntryPosition(final long configurationEntryPosition)
    {
        this.configurationEntryPosition = configurationEntryPosition;
        return this;
    }

    public int configurationEntryTerm()
    {
        return configurationEntryTerm;
    }

    public JoinResponse configurationEntryTerm(final int configurationEntryTerm)
    {
        this.configurationEntryTerm = configurationEntryTerm;
        return this;
    }

    public List<Member> members()
    {
        return members;
    }

    public JoinResponse members(final List<Member> members)
    {
        this.members.clear();
        this.members.addAll(members);
        return this;
    }

    @Override
    public int getLength()
    {
        final int size = members != null ? members.size() : 0;

        int length = headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
        length += sbeHeaderSize() + (sbeBlockLength() + hostHeaderLength()) * size;

        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            length += member.endpoint().hostLength();
        }

        return length;
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
            .status(status ? BooleanType.TRUE : BooleanType.FALSE)
            .configurationEntryPosition(configurationEntryPosition)
            .configurationEntryTerm(configurationEntryTerm);

        final int size = members != null ? members.size() : 0;

        final MembersEncoder encoder = bodyEncoder.membersCount(size);
        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            final Endpoint endpoint = member.endpoint();

            encoder.next()
                .type(MemberTypeResolver.getMemberType(member.type()))
                .port(endpoint.port())
                .putHost(endpoint.getBuffer(), hostOffset(0), endpoint.hostLength());
        }
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

        status = bodyDecoder.status() == BooleanType.TRUE;
        configurationEntryPosition = bodyDecoder.configurationEntryPosition();
        configurationEntryTerm = bodyDecoder.configurationEntryTerm();

        members.clear();
        final Iterator<MembersDecoder> iterator = bodyDecoder.members().iterator();
        while (iterator.hasNext())
        {
            final MembersDecoder decoder = iterator.next();

            final MemberType memberType = decoder.type();

            final Endpoint endpoint = new Endpoint();
            final MutableDirectBuffer endpointBuffer = (MutableDirectBuffer) endpoint.getBuffer();

            endpoint.port(decoder.port());

            final int hostLength = decoder.hostLength();
            endpointBuffer.putInt(hostLengthOffset(0), hostLength);
            decoder.getHost(endpointBuffer, hostOffset(0), hostLength);

            members.add(new Member(endpoint, MemberTypeResolver.getType(memberType)));
        }
    }

    public void reset()
    {
        log = -1;
        term = -1;
        configurationEntryPosition = -1L;
        configurationEntryTerm = -1;
        status = false;
        members.clear();
    }

}
