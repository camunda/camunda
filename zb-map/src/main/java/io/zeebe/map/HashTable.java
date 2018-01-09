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
package io.zeebe.map;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BufferUtil.ARRAY_BASE_OFFSET;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.agrona.BitUtil;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.UnsafeBuffer;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class HashTable implements Closeable
{
    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    private final UnsafeBuffer ioBuffer = new UnsafeBuffer(0, 0);
    private int length;
    private long realAddress;

    public HashTable(int tableSize)
    {
        length = Math.multiplyExact(tableSize, SIZE_OF_LONG);
        realAddress = UNSAFE.allocateMemory(length);
        clear();
    }

    @Override
    public void close() throws IOException
    {
        UNSAFE.freeMemory(realAddress);
    }

    public void clear()
    {
        UNSAFE.setMemory(realAddress, length, (byte) 0);
    }

    public int serializationSize()
    {
        return SIZE_OF_INT + length;
    }

    public int getLength()
    {
        return length;
    }

    public int getCapacity()
    {
        return length / SIZE_OF_LONG;
    }

    public void resize(int tableSize)
    {
        tableSize = BitUtil.findNextPositivePowerOfTwo(tableSize);
        final int newLength = Math.multiplyExact(tableSize, SIZE_OF_LONG);
        if (newLength > length)
        {
            final int oldLength = length;
            length = newLength;
            realAddress = UNSAFE.reallocateMemory(realAddress, length);
            // hash table was duplicated the new indices should point to the
            // same corresponding buckets like there counter-part
            UNSAFE.copyMemory(realAddress, realAddress + oldLength, length - oldLength);
        }
        else if (newLength < length)
        {
            length = newLength;
            realAddress = UNSAFE.reallocateMemory(realAddress, length);
        }
    }

    public void updateTable(int stepPower, int startIdx, long newBucketAddress)
    {
        final int mapDiff = 1 << stepPower;
        final int tableSize = getCapacity();
        for (int i = startIdx; i < tableSize; i += mapDiff)
        {
            setBucketAddress(i, newBucketAddress);
        }
    }

    // data

    public long getBucketAddress(int bucketId)
    {
        final int capacity = getCapacity();

        if (bucketId >= capacity)
        {
            throw new IllegalArgumentException("Bucket id " + bucketId + " is larger then capacity of " + capacity);
        }

        return UNSAFE.getLong(realAddress + (bucketId * SIZE_OF_LONG));
    }

    public void setBucketAddress(int bucketId, long address)
    {
        UNSAFE.putLong(realAddress + (bucketId * SIZE_OF_LONG), address);
    }

    // de-/serialize

    public void writeToStream(OutputStream outputStream, byte[] buffer) throws IOException
    {
        ioBuffer.wrap(buffer);
        ioBuffer.putInt(0, getCapacity());
        outputStream.write(buffer, 0, SIZE_OF_INT);

        for (int offset = 0; offset < length; offset += buffer.length)
        {
            final int copyLength = Math.min(buffer.length, length - offset);
            UNSAFE.copyMemory(null, realAddress + offset, buffer, ARRAY_BASE_OFFSET, copyLength);
            outputStream.write(buffer, 0, copyLength);
        }
    }


    public void readFromStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        ioBuffer.wrap(buffer);
        inputStream.read(buffer, 0, SIZE_OF_INT);
        final int newTableSize = ioBuffer.getInt(0);
        resize(newTableSize);

        int bytesRead = 0;
        for (int offset = 0; offset < length; offset += bytesRead)
        {
            final int readLength = Math.min(buffer.length, length - offset);
            bytesRead = inputStream.read(buffer, 0, readLength);

            if (bytesRead > 0)
            {
                UNSAFE.copyMemory(buffer, ARRAY_BASE_OFFSET, null, realAddress + offset, bytesRead);
            }
            else
            {
                throw new IOException("Unable to read full map buffer from input stream. " +
                    "Only read " + offset + " bytes but expected " + length + " bytes.");
            }
        }
    }
}
