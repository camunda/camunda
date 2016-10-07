package org.camunda.tngp.broker.clustering.gossip.message;

import static org.camunda.tngp.management.gossip.PeerDescriptorEncoder.hostHeaderLength;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.management.gossip.MessageHeaderDecoder;
import org.camunda.tngp.management.gossip.ProbeDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

public class ProbeReader implements BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ProbeDecoder bodyDecoder = new ProbeDecoder();

    protected final Peer member = new Peer();

    public Peer member()
    {
        return member;
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        final int hostnameOffset = offset + bodyDecoder.sbeBlockLength() + hostHeaderLength();
        final int hostnameLength = bodyDecoder.hostLength();

        member.endpoint()
            .port(bodyDecoder.port())
            .host(buffer, hostnameOffset, hostnameLength);
    }

}
