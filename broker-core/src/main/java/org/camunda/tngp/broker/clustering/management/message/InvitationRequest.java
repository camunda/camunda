package org.camunda.tngp.broker.clustering.management.message;

import static org.camunda.tngp.broker.clustering.util.EndpointDescriptor.*;
import static org.camunda.tngp.clustering.raft.JoinResponseEncoder.MembersEncoder.*;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.management.cluster.InvitationRequestDecoder;
import org.camunda.tngp.management.cluster.InvitationRequestDecoder.ClusterDecoder;
import org.camunda.tngp.management.cluster.InvitationRequestEncoder;
import org.camunda.tngp.management.cluster.InvitationRequestEncoder.ClusterEncoder;
import org.camunda.tngp.management.cluster.MessageHeaderDecoder;
import org.camunda.tngp.management.cluster.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class InvitationRequest implements BufferWriter, BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final InvitationRequestDecoder bodyDecoder = new InvitationRequestDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final InvitationRequestEncoder bodyEncoder = new InvitationRequestEncoder();

    protected int log;
    protected List<Member> cluster;

    public int log()
    {
        return log;
    }

    public InvitationRequest log(final int log)
    {
        this.log = log;
        return this;
    }

    public List<Member> cluster()
    {
        return cluster;
    }

    public InvitationRequest cluster(final List<Member> members)
    {
        this.cluster = members;
        return this;
    }

    @Override
    public int getLength()
    {
        final int size = cluster.size();

        int length = headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();

        length += sbeHeaderSize() + (sbeBlockLength() + hostHeaderLength()) * size;

        for (int i = 0; i < size; i++)
        {
            final Member member = cluster.get(i);
            final String host = member.endpoint().host();

            try
            {
                final byte[] bytes = host.getBytes("UTF-8");
                length += bytes.length;
            }
            catch (final UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }
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

        final int size = cluster.size();

        final ClusterEncoder encoder = bodyEncoder.wrap(buffer, offset)
            .log(log)
            .clusterCount(size);
        for (int i = 0; i < size; i++)
        {
            final Member member = cluster.get(i);

            encoder.next()
                .port(member.endpoint().port())
                .host(member.endpoint().host());
        }
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        log = bodyDecoder.log();

        cluster = new ArrayList<>();

        final Iterator<ClusterDecoder> iterator = bodyDecoder.cluster().iterator();

        while (iterator.hasNext())
        {
            final ClusterDecoder decoder = iterator.next();
            final Endpoint endpoint = new Endpoint();
            final MutableDirectBuffer endpointBuffer = (MutableDirectBuffer) endpoint.getBuffer();

            endpoint.port(decoder.port());

            final int hostLength = decoder.hostLength();
            endpointBuffer.putInt(hostLengthOffset(0), hostLength);
            decoder.getHost(endpointBuffer, hostOffset(0), hostLength);

            cluster.add(new Member(endpoint, Member.Type.ACTIVE));
        }
    }

}
