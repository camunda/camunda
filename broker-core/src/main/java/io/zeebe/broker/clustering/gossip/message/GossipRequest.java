/**
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
