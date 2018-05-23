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
package io.zeebe.broker.clustering.api;

import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.partitionIdNullValue;

import io.zeebe.clustering.management.ListSnapshotsRequestDecoder;
import io.zeebe.clustering.management.ListSnapshotsRequestEncoder;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class ListSnapshotsRequest implements BufferWriter, BufferReader
{
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ListSnapshotsRequestDecoder bodyDecoder = new ListSnapshotsRequestDecoder();

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final ListSnapshotsRequestEncoder bodyEncoder = new ListSnapshotsRequestEncoder();

    private int partitionId = partitionIdNullValue();

    public ListSnapshotsRequest()
    {
    }

    public ListSnapshotsRequest(final int partitionId)
    {
        this.partitionId = partitionId;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public ListSnapshotsRequest setPartitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        bodyDecoder.wrap(buffer,
                offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version());

        partitionId = bodyDecoder.partitionId();
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
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
                .partitionId(partitionId);
    }
}
