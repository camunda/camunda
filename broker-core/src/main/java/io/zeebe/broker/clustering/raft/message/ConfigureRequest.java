package io.zeebe.broker.clustering.raft.message;

import static io.zeebe.clustering.raft.ConfigureRequestEncoder.configurationEntryPositionNullValue;
import static io.zeebe.clustering.raft.ConfigureRequestEncoder.configurationEntryTermNullValue;
import static io.zeebe.clustering.raft.ConfigureRequestEncoder.partitionIdNullValue;
import static io.zeebe.clustering.raft.ConfigureRequestEncoder.termNullValue;
import static io.zeebe.clustering.raft.ConfigureRequestEncoder.topicNameHeaderLength;
import static io.zeebe.clustering.raft.ConfigureRequestEncoder.MembersEncoder.hostHeaderLength;
import static io.zeebe.clustering.raft.ConfigureRequestEncoder.MembersEncoder.sbeBlockLength;
import static io.zeebe.clustering.raft.ConfigureRequestEncoder.MembersEncoder.sbeHeaderSize;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.clustering.raft.ConfigureRequestDecoder;
import io.zeebe.clustering.raft.ConfigureRequestDecoder.MembersDecoder;
import io.zeebe.clustering.raft.ConfigureRequestEncoder;
import io.zeebe.clustering.raft.ConfigureRequestEncoder.MembersEncoder;
import io.zeebe.clustering.raft.MessageHeaderDecoder;
import io.zeebe.clustering.raft.MessageHeaderEncoder;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class ConfigureRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ConfigureRequestEncoder bodyEncoder = new ConfigureRequestEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ConfigureRequestDecoder bodyDecoder = new ConfigureRequestDecoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();
    protected int term = termNullValue();

    protected long configurationEntryPosition = configurationEntryPositionNullValue();
    protected int configurationEntryTerm = configurationEntryTermNullValue();

    protected List<Member> members = new CopyOnWriteArrayList<>();

    public DirectBuffer topicName()
    {
        return topicName;
    }

    public ConfigureRequest topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public int partitionId()
    {
        return partitionId;
    }

    public ConfigureRequest partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
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

        length += topicNameHeaderLength() + topicName.capacity();

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

        bodyEncoder.wrap(buffer, offset)
            .partitionId(partitionId)
            .term(term)
            .configurationEntryPosition(configurationEntryPosition)
            .configurationEntryTerm(configurationEntryTerm);

        final int size = members != null ? members.size() : 0;

        final MembersEncoder encoder = bodyEncoder.membersCount(size);
        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            final SocketAddress endpoint = member.endpoint();

            encoder.next()
                .port(endpoint.port())
                .putHost(endpoint.getHostBuffer(), 0, endpoint.hostLength());
        }

        bodyEncoder.putTopicName(topicName, 0, topicName.capacity());
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        partitionId = bodyDecoder.partitionId();
        term = bodyDecoder.term();
        configurationEntryPosition = bodyDecoder.configurationEntryPosition();
        configurationEntryTerm = bodyDecoder.configurationEntryTerm();

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

            // TODO: make this garbage free
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
        configurationEntryPosition = configurationEntryPositionNullValue();
        configurationEntryTerm = configurationEntryTermNullValue();
        members.clear();
    }

}
