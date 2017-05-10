package org.camunda.tngp.broker.clustering.management.message;

import static org.camunda.tngp.clustering.management.InvitationRequestEncoder.MembersEncoder.hostHeaderLength;
import static org.camunda.tngp.clustering.management.InvitationRequestEncoder.MembersEncoder.sbeBlockLength;
import static org.camunda.tngp.clustering.management.InvitationRequestEncoder.MembersEncoder.sbeHeaderSize;
import static org.camunda.tngp.clustering.management.InvitationRequestEncoder.partitionIdNullValue;
import static org.camunda.tngp.clustering.management.InvitationRequestEncoder.termNullValue;
import static org.camunda.tngp.clustering.management.InvitationRequestEncoder.topicNameHeaderLength;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
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

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();
    protected int term = termNullValue();
    protected List<Member> members = new CopyOnWriteArrayList<>();

    public int partitionId()
    {
        return partitionId;
    }

    public InvitationRequest partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public DirectBuffer topicName()
    {
        return topicName;
    }

    public InvitationRequest topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
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

        length += topicNameHeaderLength();

        if (topicName != null)
        {
            length += topicName.capacity();
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
            .partitionId(partitionId)
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

        bodyEncoder.putTopicName(topicName, 0, topicName.capacity());
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

        final int topicNameLength = bodyDecoder.topicNameLength();
        final int topicNameOffset = bodyDecoder.limit() + topicNameHeaderLength();
        topicName.wrap(buffer, topicNameOffset, topicNameLength);

        // skip topic name in decoder
        bodyDecoder.limit(topicNameOffset + topicNameLength);

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    public void reset()
    {
        topicName.wrap(0, 0);
        partitionId = partitionIdNullValue();
        term = termNullValue();
        members.clear();
    }

}
