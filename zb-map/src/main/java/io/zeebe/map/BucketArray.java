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

import static io.zeebe.map.ZbMapDescriptor.*;
import static java.lang.Math.addExact;
import static java.lang.Math.multiplyExact;
import static org.agrona.BufferUtil.ARRAY_BASE_OFFSET;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class BucketArray implements AutoCloseable
{
    public static final int ALLOCATION_FACTOR = 32;
    public static final int OVERFLOW_BUCKET_ID = -1;

    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    private final int maxBucketLength;
    private final int maxKeyLength;
    private final int maxValueLength;

    private long realAddress;
    private long length;
    private long used;
    private long occupiedBlocks;

    public BucketArray(int minBlockCount, int maxKeyLength, int maxValueLength)
    {
        this.maxBucketLength =
            addExact(BUCKET_DATA_OFFSET,
                     multiplyExact(minBlockCount, ZbMapDescriptor.getBlockLength(maxKeyLength, maxValueLength)));
        this.maxKeyLength = maxKeyLength;
        this.maxValueLength = maxValueLength;

        init();
    }

    public void clear()
    {
        close();
        init();
    }

    private void init()
    {
        try
        {
            length = addExact(BUCKET_BUFFER_HEADER_LENGTH, multiplyExact(maxBucketLength, ALLOCATION_FACTOR));
        }
        catch (final ArithmeticException e)
        {
            throw new IllegalStateException("Unable to allocate map data buffer", e);
        }

        used = BUCKET_BUFFER_HEADER_LENGTH;
        this.occupiedBlocks = 0;

        realAddress = UNSAFE.allocateMemory(length);

        UNSAFE.setMemory(realAddress, length, (byte) 0);
    }

    @Override
    public void close()
    {
        UNSAFE.freeMemory(realAddress);
    }

    public int getFirstBucketAddress()
    {
        return BUCKET_BUFFER_HEADER_LENGTH;
    }

    public long getLength()
    {
        return length;
    }

    public long getMaxBucketLength()
    {
        return maxBucketLength;
    }

    private long getRealAddress(final long offset)
    {
        return realAddress + offset;
    }

    public int getBucketCount()
    {
        return UNSAFE.getInt(getRealAddress(BUCKET_COUNT_OFFSET));
    }

    public void setBucketCount(int blockCount)
    {
        UNSAFE.putInt(getRealAddress(BUCKET_COUNT_OFFSET), blockCount);
    }

    public int getBucketFillCount(long bucketAddress)
    {
        return UNSAFE.getInt(getRealAddress(bucketAddress) + BUCKET_FILL_COUNT_OFFSET);
    }

    public void setBucketFillCount(long bucketAddress, int blockFillCount)
    {
        UNSAFE.putInt(getRealAddress(bucketAddress) + BUCKET_FILL_COUNT_OFFSET, blockFillCount);
    }

    public int getBucketLength(long bucketAddress)
    {
        return UNSAFE.getInt(getRealAddress(bucketAddress) + BUCKET_LENGTH_OFFSET);
    }

    private void setBucketLength(long bucketAddress, int newbucketLength)
    {
        UNSAFE.putInt(getRealAddress(bucketAddress) + BUCKET_LENGTH_OFFSET, newbucketLength);
    }

    public long getBucketOverflowPointer(long bucketAddress)
    {
        return UNSAFE.getLong(getRealAddress(bucketAddress) + BUCKET_OVERFLOW_POINTER_OFFSET);
    }

    public void setBucketOverflowPointer(long bucketAddress, long overflowPointer)
    {
        UNSAFE.putLong(getRealAddress(bucketAddress) + BUCKET_OVERFLOW_POINTER_OFFSET, overflowPointer);
    }

    public long getOccupiedBlocks()
    {
        return occupiedBlocks;
    }

    public int getFirstBlockOffset(long bucketAddress)
    {
        return ZbMapDescriptor.BUCKET_DATA_OFFSET;
    }

    public int getBlockLength(long bucketAddress, int blockOffset)
    {
        final int valueLength = getBlockValueLength(bucketAddress, blockOffset);
        return ZbMapDescriptor.getBlockLength(maxKeyLength, valueLength);
    }

    public int getBlockValueLength(long bucketAddress, int blockOffset)
    {
        return UNSAFE.getInt(getRealAddress(bucketAddress) + blockOffset + BLOCK_VALUE_LENGTH_OFFSET);
    }

    public void setBlockValueLength(long bucketAddress, int blockOffset, int valueLength)
    {
        UNSAFE.putInt(getRealAddress(bucketAddress) + blockOffset + BLOCK_VALUE_LENGTH_OFFSET, valueLength);
    }

    public boolean keyEquals(KeyHandler keyHandler, long bucketAddress, int blockOffset)
    {
        return keyHandler.keyEquals(getRealAddress(bucketAddress) + blockOffset + BLOCK_KEY_OFFSET);
    }

    public void readKey(KeyHandler keyHandler, long bucketAddress, int blockOffset)
    {
        keyHandler.readKey(getRealAddress(bucketAddress) + blockOffset + BLOCK_KEY_OFFSET);
    }

    public void readValue(ValueHandler valueHandler, long bucketAddress, int blockOffset)
    {
        final long valueOffset = getBlockValueOffset(getRealAddress(bucketAddress) + blockOffset, maxKeyLength);
        final int valueLength = getBlockValueLength(bucketAddress, blockOffset);

        valueHandler.readValue(valueOffset, valueLength);
    }

    public boolean updateValue(ValueHandler valueHandler, long bucketAddress, int blockOffset)
    {
        final int currBucketLength = getBucketLength(bucketAddress);
        final int currValueLength = getBlockValueLength(bucketAddress, blockOffset);

        final int valueLength = valueHandler.getValueLength();
        if (valueLength > maxValueLength)
        {
            throw new IllegalArgumentException("Value can't exceed the max value length of " + maxValueLength);
        }

        final int diff = valueLength - currValueLength;
        final int newBucketLength = currBucketLength + diff;

        final boolean canUpdate = newBucketLength <= maxBucketLength;

        if (canUpdate)
        {
            final long blockAddress = getRealAddress(bucketAddress) + blockOffset;
            if (diff != 0)
            {
                final int currBlockLength = getBlockLength(bucketAddress, blockOffset);
                final int nextBlockOffset = blockOffset + currBlockLength;

                moveRemainingMemory(bucketAddress, nextBlockOffset, diff);

                setBlockValueLength(bucketAddress, blockOffset, valueLength);
                setBucketLength(bucketAddress, newBucketLength);
            }
            valueHandler.writeValue(getBlockValueOffset(blockAddress, maxKeyLength));

        }

        return canUpdate;
    }

    public boolean addBlock(long bucketAddress, KeyHandler keyHandler, ValueHandler valueHandler)
    {
        final int blockOffset = getBucketLength(bucketAddress);
        final int blockLength = ZbMapDescriptor.getBlockLength(keyHandler.getKeyLength(), valueHandler.getValueLength());
        final int newBucketLength = blockOffset + blockLength;

        final boolean canAddRecord = newBucketLength <= maxBucketLength;

        if (canAddRecord)
        {
            final long blockAddress = getRealAddress(bucketAddress) + blockOffset;

            setBlockValueLength(bucketAddress, blockOffset, valueHandler.getValueLength());
            keyHandler.writeKey(blockAddress + BLOCK_KEY_OFFSET);
            valueHandler.writeValue(getBlockValueOffset(blockAddress, maxKeyLength));

            setBucketLength(bucketAddress, newBucketLength);
            setBucketFillCount(bucketAddress, getBucketFillCount(bucketAddress) + 1);
            occupiedBlocks++;
        }

        if (!canAddRecord)
        {
            final long overflowBucketAddress = getBucketOverflowPointer(bucketAddress);
            if (overflowBucketAddress > 0)
            {
                return addBlock(overflowBucketAddress, keyHandler, valueHandler);
            }
        }

        return canAddRecord;
    }

    public void removeBlock(long bucketAddress, int blockOffset)
    {
        final int blockLength = getBlockLength(bucketAddress, blockOffset);
        final int nextBlockOffset = blockOffset + blockLength;

        moveRemainingMemory(bucketAddress, nextBlockOffset, -blockLength);

        setBucketFillCount(bucketAddress, getBucketFillCount(bucketAddress) - 1);
        setBucketLength(bucketAddress, getBucketLength(bucketAddress) - blockLength);
        occupiedBlocks--;
    }

    public void setBucketId(long bucketAddress, int newBlockId)
    {
        UNSAFE.putInt(getRealAddress(bucketAddress) + BUCKET_ID_OFFSET, newBlockId);
    }

    public int getBucketId(long bucketAddress)
    {
        return UNSAFE.getInt(getRealAddress(bucketAddress) + BUCKET_ID_OFFSET);
    }

    public void setBucketDepth(long bucketAddress, int newBlockDepth)
    {
        UNSAFE.putInt(getRealAddress(bucketAddress) + BUCKET_DEPTH_OFFSET, newBlockDepth);
    }

    public int getBucketDepth(long bucketAddress)
    {
        return UNSAFE.getInt(getRealAddress(bucketAddress) + BUCKET_DEPTH_OFFSET);
    }

    public long overflow(long bucketAddress)
    {
        final long currentOverflowBucketAddress = getBucketOverflowPointer(bucketAddress);
        if (currentOverflowBucketAddress > 0)
        {
            return overflow(currentOverflowBucketAddress);
        }
        else
        {
            final long overflowBucketAddress = allocateNewBucket(OVERFLOW_BUCKET_ID, 0);
            setBucketOverflowPointer(bucketAddress, overflowBucketAddress);
            return overflowBucketAddress;
        }
    }

    /**
     * Allocates new bucket and returns the bucket start address.
     *
     * @param newBucketId the new block id
     * @param newBucketDepth the new block depth
     * @return the new block address
     */
    public long allocateNewBucket(int newBucketId, int newBucketDepth)
    {
        final long newUsed = used + maxBucketLength;

        if (newUsed > length)
        {
            increaseMemory(maxBucketLength);
        }

        final long bucketAddress = used;

        used = newUsed;

        setBucketId(bucketAddress, newBucketId);
        setBucketDepth(bucketAddress, newBucketDepth);
        setBucketFillCount(bucketAddress, 0);
        setBucketLength(bucketAddress, BUCKET_HEADER_LENGTH);

        setBucketCount(getBucketCount() + 1);

        return bucketAddress;
    }

    public void relocateBlock(long bucketAddress, int blockOffset, long newBucketAddress)
    {
        final long srcBlockAddress = getRealAddress(bucketAddress) + blockOffset;

        final int destBucketLength = getBucketLength(newBucketAddress);
        final long destBlockAddress = getRealAddress(newBucketAddress) + destBucketLength;

        final int blockLength = getBlockLength(bucketAddress, blockOffset);
        final int newBucketLength = destBucketLength + blockLength;

        if (newBucketLength > maxBucketLength)
        {
            // overflow
            final long overflowBucketAddress = overflow(newBucketAddress);
            relocateBlock(bucketAddress, blockOffset, overflowBucketAddress);
        }
        else
        {
            // copy to new block
            UNSAFE.copyMemory(srcBlockAddress, destBlockAddress, blockLength);
            setBucketFillCount(newBucketAddress, getBucketFillCount(newBucketAddress) + 1);
            setBucketLength(newBucketAddress, newBucketLength);

            // remove from this block (compacts this block)
            removeBlock(bucketAddress, blockOffset);
            occupiedBlocks++;
        }
    }

    public void writeToStream(OutputStream outputStream, byte[] buffer) throws IOException
    {
        for (long offset = 0; offset < length; offset += buffer.length)
        {
            final int copyLength = (int) Math.min(buffer.length, length - offset);
            UNSAFE.copyMemory(null, getRealAddress(offset), buffer, ARRAY_BASE_OFFSET, copyLength);
            outputStream.write(buffer, 0, copyLength);
        }
    }

    public void readFromStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        used = 0;

        long bytesRead;
        while ((bytesRead = inputStream.read(buffer)) > 0)
        {
            if (used + bytesRead > length)
            {
                increaseMemory(buffer.length);
            }

            UNSAFE.copyMemory(buffer, ARRAY_BASE_OFFSET, null, getRealAddress(used), bytesRead);
            used += bytesRead;
        }
    }

    private void moveRemainingMemory(long bucketAddress, int srcOffset, int moveBytes)
    {
        final int bucketLength = getBucketLength(bucketAddress);

        if (srcOffset < bucketLength)
        {
            final long srcAddress = getRealAddress(bucketAddress) + srcOffset;
            final int remainingBytes = bucketLength - srcOffset;

            UNSAFE.copyMemory(srcAddress, srcAddress + moveBytes, remainingBytes);
        }
    }

    private void increaseMemory(final long addition)
    {
        final long newLength;
        try
        {
            newLength = addExact(length, multiplyExact(addition, ALLOCATION_FACTOR));
        }
        catch (final ArithmeticException e)
        {
            throw new IllegalStateException("Unable increase map data buffer", e);
        }

        realAddress = UNSAFE.reallocateMemory(realAddress, newLength);
        UNSAFE.setMemory(realAddress + length, newLength - length, (byte) 0);
        length = newLength;

    }
}
