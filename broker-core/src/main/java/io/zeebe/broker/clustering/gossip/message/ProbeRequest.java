package io.zeebe.broker.clustering.gossip.message;

import static io.zeebe.clustering.gossip.ProbeEncoder.hostHeaderLength;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.clustering.gossip.MessageHeaderDecoder;
import io.zeebe.clustering.gossip.MessageHeaderEncoder;
import io.zeebe.clustering.gossip.ProbeDecoder;
import io.zeebe.clustering.gossip.ProbeEncoder;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class ProbeRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ProbeDecoder bodyDecoder = new ProbeDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ProbeEncoder bodyEncoder = new ProbeEncoder();

    protected final SocketAddress target = new SocketAddress();

    public SocketAddress target()
    {
        return target;
    }

    public ProbeRequest target(final SocketAddress target)
    {
        this.target.wrap(target);
        return this;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                hostHeaderLength() +
                target.hostLength();
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
            .port(target.port())
            .putHost(target.getHostBuffer(), 0, target.hostLength());
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        final int frameEnd = offset + length;

        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        final int hostLength = bodyDecoder.hostLength();

        target.port(bodyDecoder.port());
        target.hostLength(hostLength);
        bodyDecoder.getHost(target.getHostBuffer(), 0, hostLength);

        assert bodyDecoder.limit() == frameEnd : "Decoder read only to position " + bodyDecoder.limit() + " but expected " + frameEnd + " as final position";
    }

    public void reset()
    {
        target.reset();
    }

}
