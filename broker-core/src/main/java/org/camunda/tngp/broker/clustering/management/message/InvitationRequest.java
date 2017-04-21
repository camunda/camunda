package org.camunda.tngp.broker.clustering.management.message;

import static org.camunda.tngp.clustering.management.InvitationRequestDecoder.MembersDecoder.hostHeaderLength;
import static org.camunda.tngp.clustering.management.InvitationRequestDecoder.MembersDecoder.sbeBlockLength;
import static org.camunda.tngp.clustering.management.InvitationRequestDecoder.MembersDecoder.sbeHeaderSize;
import static org.camunda.tngp.clustering.management.InvitationRequestDecoder.logNameHeaderLength;
import static org.camunda.tngp.util.StringUtil.getBytes;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.clustering.management.InvitationRequestDecoder;
import org.camunda.tngp.clustering.management.InvitationRequestDecoder.MembersDecoder;
import org.camunda.tngp.clustering.management.InvitationRequestEncoder;
import org.camunda.tngp.clustering.management.InvitationRequestEncoder.MembersEncoder;
import org.camunda.tngp.clustering.management.MessageHeaderDecoder;
import org.camunda.tngp.clustering.management.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class InvitationRequest implements BufferWriter, BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final InvitationRequestDecoder bodyDecoder = new InvitationRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final InvitationRequestEncoder bodyEncoder = new InvitationRequestEncoder();

    protected int id;
    protected String name;
    protected int term;
    protected List<Member> members = new CopyOnWriteArrayList<>();

    public int id()
    {
        return id;
    }

    public InvitationRequest id(final int id)
    {
        this.id = id;
        return this;
    }

    public String name()
    {
        return name;
    }

    public InvitationRequest name(final String name)
    {
        this.name = name;
        return this;
    }

    public int term()
    {
        return term;
    }

    public InvitationRequest term(final int term)
    {
        this.term = term;
        return this;
    }

    public List<Member> members()
    {
        return members;
    }

    public InvitationRequest members(final List<Member> members)
    {
        this.members.clear();
        this.members.addAll(members);
        return this;
    }

    @Override
    public int getLength()
    {
        final int size = members.size();

        int length = headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();

        length += sbeHeaderSize() + (sbeBlockLength() + hostHeaderLength()) * size;

        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            final Endpoint endpoint = member.endpoint();
            length += endpoint.hostLength();
        }

        length += logNameHeaderLength();

        if (name != null && !name.isEmpty())
        {
            length += getBytes(name).length;
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

        final int size = members.size();

        final MembersEncoder encoder = bodyEncoder.wrap(buffer, offset)
            .id(id)
            .term(term)
            .membersCount(size);

        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            final Endpoint endpoint = member.endpoint();

            encoder.next()
                .port(endpoint.port())
                .putHost(endpoint.getHostBuffer(), 0, endpoint.hostLength());
        }

        bodyEncoder.logName(name);
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        id = bodyDecoder.id();
        term = bodyDecoder.term();

        members.clear();

        final Iterator<MembersDecoder> iterator = bodyDecoder.members().iterator();

        while (iterator.hasNext())
        {
            final MembersDecoder decoder = iterator.next();

            final Member member = new Member();
            member.endpoint().port(decoder.port());

            final MutableDirectBuffer endpointBuffer = member.endpoint().getHostBuffer();
            final int hostLength = decoder.hostLength();
            member.endpoint().hostLength(hostLength);
            decoder.getHost(endpointBuffer, 0, hostLength);

            members.add(member);
        }

        name = bodyDecoder.logName();
    }

    public void reset()
    {
        members.clear();
        id = -1;
        term = -1;
        name = null;
    }

}
