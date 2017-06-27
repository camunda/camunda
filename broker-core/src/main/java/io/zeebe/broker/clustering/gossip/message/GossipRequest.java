package io.zeebe.broker.clustering.gossip.message;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.message.util.GossipMessageReader;
import io.zeebe.broker.clustering.gossip.message.util.GossipMessageWriter;
import io.zeebe.clustering.gossip.GossipEncoder;
import io.zeebe.clustering.gossip.MessageHeaderDecoder;
import io.zeebe.clustering.gossip.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class GossipRequest implements BufferReader, BufferWriter
{
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final GossipEncoder bodyEncoder = new GossipEncoder();

    private final GossipMessageReader gossipReader = new GossipMessageReader();
    private final GossipMessageWriter gossipWriter = new GossipMessageWriter();

    public Iterator<Peer> peers()
    {
        return gossipReader;
    }

    public GossipRequest peers(final PeerList peers)
    {
        gossipWriter.peers(peers);
        return this;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() + gossipWriter.getLength();
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

        gossipWriter.write(buffer, offset);
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();
        length -= headerDecoder.encodedLength();
        gossipReader.wrap(buffer, offset, length);
    }

}
