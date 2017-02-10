package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.hostLengthOffset;
import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.hostOffset;
import static org.camunda.tngp.clustering.raft.ConfigureRequestDecoder.MembersDecoder.hostHeaderLength;
import static org.camunda.tngp.clustering.raft.ConfigureRequestDecoder.MembersDecoder.sbeBlockLength;
import static org.camunda.tngp.clustering.raft.ConfigureRequestDecoder.MembersDecoder.sbeHeaderSize;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.util.MemberTypeResolver;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.clustering.raft.ConfigureRequestDecoder;
import org.camunda.tngp.clustering.raft.ConfigureRequestDecoder.MembersDecoder;
import org.camunda.tngp.clustering.raft.ConfigureRequestEncoder;
import org.camunda.tngp.clustering.raft.ConfigureRequestEncoder.MembersEncoder;
import org.camunda.tngp.clustering.raft.MemberType;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.clustering.raft.RaftHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ConfigureRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ConfigureRequestEncoder bodyEncoder = new ConfigureRequestEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ConfigureRequestDecoder bodyDecoder = new ConfigureRequestDecoder();

    protected int log;
    protected int term;
    protected long configurationEntryPosition;
    protected int configurationEntryTerm;
    protected List<Member> members;

    public int log()
    {
        return log;
    }

    public ConfigureRequest log(final int log)
    {
        this.log = log;
        return this;
    }

    public int term()
    {
        return term;
    }

    public ConfigureRequest term(final int term)
    {
        this.term = term;
        return this;
    }

    public long configurationEntryPosition()
    {
        return configurationEntryPosition;
    }

    public ConfigureRequest configurationEntryPosition(final long configurationEntryPosition)
    {
        this.configurationEntryPosition = configurationEntryPosition;
        return this;
    }

    public int configurationEntryTerm()
    {
        return configurationEntryTerm;
    }

    public ConfigureRequest configurationEntryTerm(final int configurationEntryTerm)
    {
        this.configurationEntryTerm = configurationEntryTerm;
        return this;
    }

    public List<Member> members()
    {
        return members;
    }

    public ConfigureRequest members(final List<Member> members)
    {
        this.members = members;
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
    public void write(MutableDirectBuffer buffer, int offset)
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
            .configurationEntryPosition(configurationEntryPosition)
            .configurationEntryTerm(configurationEntryTerm);

        final int size = members != null ? members.size() : 0;

        final MembersEncoder encoder = bodyEncoder.membersCount(size);
        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            final Endpoint endpoint = member.endpoint();

            encoder.next()
                .memberType(MemberTypeResolver.getMemberType(member.type()))
                .port(endpoint.port())
                .putHost(endpoint.getBuffer(), hostOffset(0), endpoint.hostLength());
        }
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        final RaftHeaderDecoder raftHeaderDecoder = bodyDecoder.header();

        log = raftHeaderDecoder.log();
        term = raftHeaderDecoder.term();

        configurationEntryPosition = bodyDecoder.configurationEntryPosition();
        configurationEntryTerm = bodyDecoder.configurationEntryTerm();

        // TODO: make this garbage free if necessary
        members = new ArrayList<>();

        final Iterator<MembersDecoder> iterator = bodyDecoder.members().iterator();
        while (iterator.hasNext())
        {
            final MembersDecoder decoder = iterator.next();

            final MemberType memberType = decoder.memberType();

            final Endpoint endpoint = new Endpoint();
            final MutableDirectBuffer endpointBuffer = (MutableDirectBuffer) endpoint.getBuffer();

            endpoint.port(decoder.port());

            final int hostLength = decoder.hostLength();
            endpointBuffer.putInt(hostLengthOffset(0), hostLength);
            decoder.getHost(endpointBuffer, hostOffset(0), hostLength);

            members.add(new Member(endpoint, MemberTypeResolver.getType(memberType)));
        }
    }

}
