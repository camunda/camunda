package io.zeebe.broker.clustering.raft.message;

import static io.zeebe.clustering.raft.LeaveResponseEncoder.configurationEntryPositionNullValue;
import static io.zeebe.clustering.raft.LeaveResponseEncoder.configurationEntryTermNullValue;
import static io.zeebe.clustering.raft.LeaveResponseEncoder.termNullValue;
import static io.zeebe.clustering.raft.LeaveResponseEncoder.MembersEncoder.hostHeaderLength;
import static io.zeebe.clustering.raft.LeaveResponseEncoder.MembersEncoder.sbeBlockLength;
import static io.zeebe.clustering.raft.LeaveResponseEncoder.MembersEncoder.sbeHeaderSize;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.clustering.raft.BooleanType;
import io.zeebe.clustering.raft.LeaveResponseDecoder;
import io.zeebe.clustering.raft.LeaveResponseDecoder.MembersDecoder;
import io.zeebe.clustering.raft.LeaveResponseEncoder;
import io.zeebe.clustering.raft.LeaveResponseEncoder.MembersEncoder;
import io.zeebe.clustering.raft.MessageHeaderDecoder;
import io.zeebe.clustering.raft.MessageHeaderEncoder;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class LeaveResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final LeaveResponseDecoder bodyDecoder = new LeaveResponseDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final LeaveResponseEncoder bodyEncoder = new LeaveResponseEncoder();

    protected int term = termNullValue();

    protected long configurationEntryPosition = configurationEntryPositionNullValue();
    protected int configurationEntryTerm = configurationEntryTermNullValue();
    protected boolean succeeded;
    protected List<Member> members = new CopyOnWriteArrayList<>();

    public int term()
    {
        return term;
    }

    public LeaveResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    public boolean succeeded()
    {
        return succeeded;
    }

    public LeaveResponse succeeded(final boolean succeeded)
    {
        this.succeeded = succeeded;
        return this;
    }

    public long configurationEntryPosition()
    {
        return configurationEntryPosition;
    }

    public LeaveResponse configurationEntryPosition(final long configurationEntryPosition)
    {
        this.configurationEntryPosition = configurationEntryPosition;
        return this;
    }

    public int configurationEntryTerm()
    {
        return configurationEntryTerm;
    }

    public LeaveResponse configurationEntryTerm(final int configurationEntryTerm)
    {
        this.configurationEntryTerm = configurationEntryTerm;
        return this;
    }

    public List<Member> members()
    {
        return members;
    }

    public LeaveResponse members(final List<Member> members)
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

        bodyEncoder.wrap(buffer, offset)
            .term(term)
            .succeeded(succeeded ? BooleanType.TRUE : BooleanType.FALSE)
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
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        term = bodyDecoder.term();
        succeeded = bodyDecoder.succeeded() == BooleanType.TRUE;
        configurationEntryPosition = bodyDecoder.configurationEntryPosition();
        configurationEntryTerm = bodyDecoder.configurationEntryTerm();

        members.clear();

        final Iterator<MembersDecoder> iterator = bodyDecoder.members().iterator();
        while (iterator.hasNext())
        {
            final MembersDecoder decoder = iterator.next();

            final Member member = new Member();
            member.endpoint().port(decoder.port());

            final int hostLength = decoder.hostLength();
            final MutableDirectBuffer endpointBuffer = member.endpoint().getHostBuffer();
            member.endpoint().hostLength(hostLength);
            decoder.getHost(endpointBuffer, 0, hostLength);

            // TODO: make this garbage free
            members.add(member);
        }

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    public void reset()
    {
        term = termNullValue();
        configurationEntryPosition = configurationEntryPositionNullValue();
        configurationEntryTerm = configurationEntryTermNullValue();
        succeeded = false;
        members.clear();
    }
}
