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

import io.zeebe.broker.util.SbeBufferWriterReader;
import io.zeebe.clustering.management.ListSnapshotsRequestDecoder;
import io.zeebe.clustering.management.ListSnapshotsRequestEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class ListSnapshotsRequest extends SbeBufferWriterReader<ListSnapshotsRequestEncoder, ListSnapshotsRequestDecoder>
{
    private final ListSnapshotsRequestDecoder bodyDecoder = new ListSnapshotsRequestDecoder();
    private final ListSnapshotsRequestEncoder bodyEncoder = new ListSnapshotsRequestEncoder();

    private int partitionId = partitionIdNullValue();

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
    public void reset()
    {
        super.reset();
        this.partitionId = partitionIdNullValue();
    }

    @Override
    protected ListSnapshotsRequestEncoder getBodyEncoder()
    {
        return bodyEncoder;
    }

    @Override
    protected ListSnapshotsRequestDecoder getBodyDecoder()
    {
        return bodyDecoder;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        super.wrap(buffer, offset, length);
        partitionId = bodyDecoder.partitionId();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        super.write(buffer, offset);
        bodyEncoder.partitionId(partitionId);
    }
}
