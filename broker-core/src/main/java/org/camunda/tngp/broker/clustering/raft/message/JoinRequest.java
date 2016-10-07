package org.camunda.tngp.broker.clustering.raft.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.protocol.Member.Type;
import org.camunda.tngp.broker.clustering.raft.util.MemberTypeResolver;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.clustering.raft.JoinRequestDecoder;
import org.camunda.tngp.clustering.raft.JoinRequestEncoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class JoinRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final JoinRequestEncoder bodyEncoder = new JoinRequestEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final JoinRequestDecoder bodyDecoder = new JoinRequestDecoder();

    protected int log;
    protected final Endpoint endpoint = new Endpoint();
    protected final Member member = new Member(endpoint, Member.Type.INACTIVE);
    protected boolean isMemberAvailable = false;

    public int log()
    {
        return log;
    }

    public JoinRequest log(final int log)
    {
        this.log = log;
        return this;
    }

    public Member member()
    {
        return member;
    }

    public JoinRequest member(final Member member)
    {
        if (member != null)
        {
            endpoint.wrap(member.endpoint());
            this.member.type(member.type());
            isMemberAvailable = true;
        }
        else
        {
            endpoint.clear();
            this.member.type(Member.Type.INACTIVE);
            isMemberAvailable = false;
        }

        return this;
    }

    @Override
    public int getLength()
    {
        int size = headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                JoinRequestEncoder.hostHeaderLength();

        if (isMemberAvailable)
        {
            size += endpoint.hostLength();
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
            .term(-1);

        bodyEncoder
            .type(MemberTypeResolver.getMemberType(member.type()))
            .port(endpoint.port());

        bodyEncoder.putHost(endpoint.getBuffer(), hostOffset(0), endpoint.hostLength());
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        log = bodyDecoder.header().log();

        isMemberAvailable = false;
        endpoint.clear();

        final int hostLength = bodyDecoder.hostLength();
        if (hostLength > 0)
        {
            endpoint.port(bodyDecoder.port());
            final MutableDirectBuffer endpointBuffer = (MutableDirectBuffer) endpoint.getBuffer();

            endpointBuffer.putInt(hostLengthOffset(0), hostLength);
            bodyDecoder.getHost(endpointBuffer, hostOffset(0), hostLength);

            member.type(MemberTypeResolver.getType(bodyDecoder.type()));
            isMemberAvailable = true;
        }
    }

    public void reset()
    {
        log = -1;
        isMemberAvailable = false;
        endpoint.clear();
        member.type(Type.INACTIVE);
    }

}
