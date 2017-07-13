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
package io.zeebe.hashindex;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BufferUtil.ARRAY_BASE_OFFSET;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class HashIndexBuffer implements Closeable
{
    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    private final int length;
    private final long addr;

    public HashIndexBuffer(int indexSize)
    {
        length = Math.multiplyExact(indexSize, SIZE_OF_LONG);
        addr = UNSAFE.allocateMemory(length);
        clear();
    }

    @Override
    public void close() throws IOException
    {
        UNSAFE.freeMemory(addr);
    }

    public long getBlockOffset(int blockId)
    {
        return UNSAFE.getLong(addr + (blockId * SIZE_OF_LONG));
    }

    public void setBlockOffset(int blockId, long offset)
    {
        UNSAFE.putLong(addr + (blockId * SIZE_OF_LONG), offset);
    }

    public void clear()
    {
        UNSAFE.setMemory(addr, length, (byte) 0);
    }

    public void writeToStream(OutputStream outputStream, byte[] buffer) throws IOException
    {
        for (int offset = 0; offset < length; offset += buffer.length)
        {
            final int copyLength = Math.min(buffer.length, length - offset);
            UNSAFE.copyMemory(null, addr + offset, buffer, ARRAY_BASE_OFFSET, copyLength);
            outputStream.write(buffer, 0, copyLength);
        }
    }

    public void readFromStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        int bytesRead = 0;
        for (int offset = 0; offset < length; offset += bytesRead)
        {
            final int readLength = Math.min(buffer.length, length - offset);
            bytesRead = inputStream.read(buffer, 0, readLength);

            if (bytesRead > 0)
            {
                UNSAFE.copyMemory(buffer, ARRAY_BASE_OFFSET, null, addr + offset, bytesRead);
            }
            else
            {
                throw new IOException("Unable to read full index buffer from input stream. " +
                    "Only read " + offset + " bytes but expected " + length + " bytes.");
            }
        }
    }
}
