package org.camunda.tngp.broker.clustering.raft.entry;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.hostLengthOffset;
import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.hostOffset;
import static org.camunda.tngp.clustering.raft.ConfigurationEntryDecoder.MembersDecoder.hostHeaderLength;
import static org.camunda.tngp.clustering.raft.ConfigurationEntryDecoder.MembersDecoder.sbeBlockLength;
import static org.camunda.tngp.clustering.raft.ConfigurationEntryDecoder.MembersDecoder.sbeHeaderSize;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.util.MemberTypeResolver;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.clustering.raft.ConfigurationEntryDecoder;
import org.camunda.tngp.clustering.raft.ConfigurationEntryDecoder.MembersDecoder;
import org.camunda.tngp.clustering.raft.ConfigurationEntryEncoder;
import org.camunda.tngp.clustering.raft.ConfigurationEntryEncoder.MembersEncoder;
import org.camunda.tngp.clustering.raft.MemberType;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ConfigurationEntry implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ConfigurationEntryDecoder bodyDecoder = new ConfigurationEntryDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ConfigurationEntryEncoder bodyEncoder = new ConfigurationEntryEncoder();

    protected List<Member> members = new CopyOnWriteArrayList<>();

    public List<Member> members()
    {
        return members;
    }

    public ConfigurationEntry members(final List<Member> members)
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
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset);

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

        members.clear();

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

    public void reset()
    {
        members.clear();
    }

}
