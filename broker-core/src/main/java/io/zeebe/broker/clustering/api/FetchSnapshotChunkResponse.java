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

import static io.zeebe.clustering.management.FetchSnapshotChunkResponseEncoder.dataHeaderLength;

import io.zeebe.broker.util.SbeBufferWriterReader;
import io.zeebe.clustering.management.FetchSnapshotChunkResponseDecoder;
import io.zeebe.clustering.management.FetchSnapshotChunkResponseEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.ArrayUtil;
import org.agrona.concurrent.UnsafeBuffer;

public class FetchSnapshotChunkResponse extends SbeBufferWriterReader<FetchSnapshotChunkResponseEncoder, FetchSnapshotChunkResponseDecoder>
{
    private final FetchSnapshotChunkResponseDecoder bodyDecoder = new FetchSnapshotChunkResponseDecoder();
    private final FetchSnapshotChunkResponseEncoder bodyEncoder = new FetchSnapshotChunkResponseEncoder();

    private final DirectBuffer data = new UnsafeBuffer(0, 0);

    public DirectBuffer getData()
    {
        return data;
    }

    public FetchSnapshotChunkResponse setData(final byte[] data, final int offset, final int length)
    {
        this.data.wrap(data, offset, length);
        return this;
    }

    public FetchSnapshotChunkResponse setData(final DirectBuffer data)
    {
        this.data.wrap(data);
        return this;
    }

    @Override
    public void reset()
    {
        super.reset();
        this.data.wrap(ArrayUtil.EMPTY_BYTE_ARRAY);
    }

    @Override
    protected FetchSnapshotChunkResponseEncoder getBodyEncoder()
    {
        return bodyEncoder;
    }

    @Override
    protected FetchSnapshotChunkResponseDecoder getBodyDecoder()
    {
        return bodyDecoder;
    }

    @Override
    public int getLength()
    {
        return super.getLength() + dataHeaderLength() + data.capacity();
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length)
    {
        super.wrap(buffer, offset, length);
        data.wrap(buffer, bodyDecoder.limit() + dataHeaderLength(), bodyDecoder.dataLength());
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset)
    {
        super.write(buffer, offset);
        bodyEncoder.putData(data, 0, data.capacity());
    }
}
