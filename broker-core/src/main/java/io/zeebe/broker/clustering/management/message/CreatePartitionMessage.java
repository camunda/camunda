/*
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
package io.zeebe.broker.clustering.management.message;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.clustering.management.CreatePartitionMessageDecoder;
import io.zeebe.clustering.management.CreatePartitionMessageEncoder;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class CreatePartitionMessage implements BufferReader, BufferWriter
{

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final CreatePartitionMessageEncoder bodyEncoder = new CreatePartitionMessageEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final CreatePartitionMessageDecoder bodyDecoder = new CreatePartitionMessageDecoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = CreatePartitionMessageEncoder.partitionIdNullValue();

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                CreatePartitionMessageEncoder.topicNameHeaderLength() +
                topicName.capacity();
    }

    public CreatePartitionMessage topicName(DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public CreatePartitionMessage partitionId(int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public DirectBuffer getTopicName()
    {
        return topicName;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .partitionId(partitionId)
            .putTopicName(topicName, 0, topicName.capacity());
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer,
                offset,
                headerDecoder.blockLength(),
                headerDecoder.version());

        partitionId = bodyDecoder.partitionId();

        offset += headerDecoder.blockLength();

        final int topicNameLength = bodyDecoder.topicNameLength();
        offset += CreatePartitionMessageDecoder.topicNameHeaderLength();

        topicName.wrap(buffer, offset, topicNameLength);
    }

}
