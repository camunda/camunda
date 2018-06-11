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

import static io.zeebe.clustering.management.FetchSnapshotChunkRequestEncoder.*;
import static io.zeebe.util.StringUtil.getBytes;

import io.zeebe.broker.util.SbeBufferWriterReader;
import io.zeebe.clustering.management.FetchSnapshotChunkRequestDecoder;
import io.zeebe.clustering.management.FetchSnapshotChunkRequestEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.ArrayUtil;
import org.agrona.concurrent.UnsafeBuffer;

public class FetchSnapshotChunkRequest extends SbeBufferWriterReader<FetchSnapshotChunkRequestEncoder, FetchSnapshotChunkRequestDecoder>
{
    private final FetchSnapshotChunkRequestDecoder bodyDecoder = new FetchSnapshotChunkRequestDecoder();
    private final FetchSnapshotChunkRequestEncoder bodyEncoder = new FetchSnapshotChunkRequestEncoder();

    private int partitionId = partitionIdNullValue();
    private final UnsafeBuffer name = new UnsafeBuffer(0, 0);
    private long logPosition = logPositionNullValue();
    private int chunkOffset = chunkOffsetNullValue();
    private int chunkLength = chunkLengthNullValue();

    @Override
    public void reset()
    {
        super.reset();
        this.setPartitionId(partitionIdNullValue());
        this.setLogPosition(logPositionNullValue());
        this.name.wrap(ArrayUtil.EMPTY_BYTE_ARRAY);
        this.setChunkLength(chunkLengthNullValue());
        this.setChunkOffset(chunkOffsetNullValue());
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public FetchSnapshotChunkRequest setPartitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public DirectBuffer getName()
    {
        return name;
    }

    public FetchSnapshotChunkRequest setName(final String name)
    {
        this.name.wrap(getBytes(name));
        return this;
    }

    public FetchSnapshotChunkRequest setName(final DirectBuffer name)
    {
        this.name.wrap(name);
        return this;
    }

    public long getLogPosition()
    {
        return logPosition;
    }

    public FetchSnapshotChunkRequest setLogPosition(final long logPosition)
    {
        this.logPosition = logPosition;
        return this;
    }

    public int getChunkOffset()
    {
        return chunkOffset;
    }

    public FetchSnapshotChunkRequest setChunkOffset(final int chunkOffset)
    {
        this.chunkOffset = chunkOffset;
        return this;
    }

    public int getChunkLength()
    {
        return chunkLength;
    }

    public FetchSnapshotChunkRequest setChunkLength(final int chunkLength)
    {
        this.chunkLength = chunkLength;
        return this;
    }

    @Override
    protected FetchSnapshotChunkRequestEncoder getBodyEncoder()
    {
        return bodyEncoder;
    }

    @Override
    protected FetchSnapshotChunkRequestDecoder getBodyDecoder()
    {
        return bodyDecoder;
    }

    @Override
    public int getLength()
    {
        return super.getLength() + nameHeaderLength() + name.capacity();
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length)
    {
        super.wrap(buffer, offset, length);

        partitionId = bodyDecoder.partitionId();
        logPosition = bodyDecoder.logPosition();
        chunkOffset = bodyDecoder.chunkOffset();
        chunkLength = bodyDecoder.chunkLength();
        name.wrap(buffer, bodyDecoder.limit() + nameHeaderLength(), bodyDecoder.nameLength());
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset)
    {
        super.write(buffer, offset);

        bodyEncoder.chunkLength(chunkLength);
        bodyEncoder.chunkOffset(chunkOffset);
        bodyEncoder.partitionId(partitionId);
        bodyEncoder.logPosition(logPosition);
        bodyEncoder.putName(name, 0, name.capacity());
    }
}
