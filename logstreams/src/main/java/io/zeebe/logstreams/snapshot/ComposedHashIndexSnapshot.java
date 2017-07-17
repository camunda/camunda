/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.snapshot;

import io.zeebe.logstreams.spi.SnapshotSupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static io.zeebe.util.StreamUtil.readLong;
import static io.zeebe.util.StreamUtil.writeLong;
import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.agrona.BitUtil.SIZE_OF_LONG;

/**
 * A composition of one or more snapshots which are combined to a single snapshot.
 */
public class ComposedHashIndexSnapshot implements SnapshotSupport
{
    protected final HashIndexSnapshotSupport[] parts;
    protected final byte count;
    protected long processedBytes;

    public ComposedHashIndexSnapshot(HashIndexSnapshotSupport... parts)
    {
        this.parts = parts;
        this.count = (byte) parts.length;

        if (count != parts.length)
        {
            throw new IllegalArgumentException("Cannot contain more than 255 parts");
        }

        if (count < 1)
        {
            throw new IllegalArgumentException("must contains at least one part");
        }
    }

    public long getProcessedBytes()
    {
        return processedBytes;
    }

    @Override
    public void writeSnapshot(OutputStream outputStream) throws Exception
    {
        outputStream.write(count);
        long writtenBytes = SIZE_OF_BYTE;

        for (byte i = 0; i < count; i++)
        {
            final HashIndexSnapshotSupport part = parts[i];
            final long hashIndexSize = part.snapshotSize();
            writeLong(outputStream, hashIndexSize);
            writtenBytes += SIZE_OF_LONG;
            part.writeSnapshot(outputStream);
            writtenBytes += hashIndexSize;
        }
        processedBytes = writtenBytes;
    }

    @Override
    public void recoverFromSnapshot(InputStream inputStream) throws Exception
    {
        final LimitedInputStream limitedInputStream = new LimitedInputStream(inputStream);
        final byte dataCount = limitedInputStream.readByte();
        long bytesRead = SIZE_OF_BYTE;

        if (dataCount != count)
        {
            throw new IllegalStateException("illegal data of composed snapshot, expected " + count + " parts but found " + dataCount);
        }

        for (byte idx = 0; idx < count; idx++)
        {
            limitedInputStream.reset();
            final long hashIndexSize = readLong(inputStream);
            bytesRead += SIZE_OF_LONG;
            limitedInputStream.setLimit(hashIndexSize);
            parts[idx].recoverFromSnapshot(limitedInputStream);
            bytesRead += hashIndexSize;
        }
        processedBytes = bytesRead;
    }

    @Override
    public void reset()
    {
        for (int i = 0; i < count; i++)
        {
            parts[i].reset();
        }
    }

    private static final class LimitedInputStream extends InputStream
    {
        private long byteCount = 0L;
        private long limit = Long.MAX_VALUE;
        private final InputStream inputStream;

        LimitedInputStream(InputStream inputStream)
        {
            this.inputStream = inputStream;
        }

        public void reset()
        {
            byteCount = 0L;
            limit = Long.MAX_VALUE;
        }

        public void setLimit(long limit)
        {
            this.limit = limit;
        }

        public byte readByte() throws IOException
        {
            return (byte) read();
        }

        @Override
        public int read() throws IOException
        {
            if (byteCount >= limit)
            {
                return 0;
            }
            else
            {
                ++byteCount;
                return inputStream.read();
            }
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException
        {
            if (byteCount >= limit)
            {
                return 0;
            }
            if (byteCount + len >= limit)
            {
                len = (int) (limit - byteCount);
            }
            final int readCount = inputStream.read(b, off, len);
            byteCount += readCount;
            return readCount;
        }

        @Override
        public int read(byte b[]) throws IOException
        {
            return this.read(b, 0, b.length);
        }
    }
}
