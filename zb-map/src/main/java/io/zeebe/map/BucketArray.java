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
    private final int maxBucketBlockCount;
    private final int maxKeyLength;
    private final int maxValueLength;

    private long realAddress;
    private long capacity;

    private long countOfUsedBytes;
    private long occupiedBlocks;

    public BucketArray(int maxBucketBlockCount, int maxKeyLength, int maxValueLength)
    {
        this.maxBucketLength =
            addExact(BUCKET_DATA_OFFSET,
                     multiplyExact(maxBucketBlockCount, ZbMapDescriptor.getBlockLength(maxKeyLength, maxValueLength)));
        this.maxBucketBlockCount = maxBucketBlockCount;
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
            capacity = addExact(BUCKET_BUFFER_HEADER_LENGTH, multiplyExact(maxBucketLength, ALLOCATION_FACTOR));
        }
        catch (final ArithmeticException e)
        {
            throw new IllegalStateException("Unable to allocate map data buffer", e);
        }

        countOfUsedBytes = BUCKET_BUFFER_HEADER_LENGTH;
        this.occupiedBlocks = 0;

        realAddress = UNSAFE.allocateMemory(capacity);

        // init overflow pointers and bucket count - rest is not necessary
        clearOverflowPointers(countOfUsedBytes, ALLOCATION_FACTOR);
        setBucketCount(0);
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

    public long getCapacity()
    {
        return capacity;
    }

    public long getCountOfUsedBytes()
    {
        return countOfUsedBytes;
    }

    public float getLoadFactor()
    {
        final int bucketCount = getBucketCount();
        if (bucketCount <= 0)
        {
            return 0.0F;
        }
        else
        {
            return (float) occupiedBlocks / (float) (bucketCount * maxBucketBlockCount);
        }
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
        return ZbMapDescriptor.getBlockLength(maxKeyLength, maxValueLength);
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
        valueHandler.readValue(valueOffset, maxValueLength);
    }

    public void updateValue(ValueHandler valueHandler, long bucketAddress, int blockOffset)
    {
        final int valueLength = valueHandler.getValueLength();
        if (valueLength > maxValueLength)
        {
            throw new IllegalArgumentException("Value can't exceed the max value length of " + maxValueLength);
        }

        final long blockAddress = getRealAddress(bucketAddress) + blockOffset;
        valueHandler.writeValue(getBlockValueOffset(blockAddress, maxKeyLength));
    }

    public boolean addBlock(long bucketAddress, KeyHandler keyHandler, ValueHandler valueHandler)
    {
        final int bucketFillCount = getBucketFillCount(bucketAddress);
        final boolean canAddRecord = bucketFillCount < maxBucketBlockCount;

        if (canAddRecord)
        {
            final int blockOffset = getBucketLength(bucketAddress);
            final int blockLength = ZbMapDescriptor.getBlockLength(maxKeyLength, maxValueLength);
            final int newBucketLength = blockOffset + blockLength;

            final long blockAddress = getRealAddress(bucketAddress) + blockOffset;

            keyHandler.writeKey(blockAddress + BLOCK_KEY_OFFSET);
            valueHandler.writeValue(getBlockValueOffset(blockAddress, maxKeyLength));

            setBucketFillStatus(bucketAddress, bucketFillCount + 1, newBucketLength);
            occupiedBlocks++;
        }
        else
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
        removeBlockFromBucket(bucketAddress, blockOffset);
        occupiedBlocks--;
    }

    private void removeBlockFromBucket(long bucketAddress, int blockOffset)
    {
        final int blockLength = getBlockLength(bucketAddress, blockOffset);
        final int nextBlockOffset = blockOffset + blockLength;

        moveRemainingMemory(bucketAddress, nextBlockOffset, -blockLength);

        setBucketFillStatus(bucketAddress,
                               getBucketFillCount(bucketAddress) - 1,
                               getBucketLength(bucketAddress) - blockLength);
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
        final long newUsed = countOfUsedBytes + maxBucketLength;

        if (newUsed > capacity)
        {
            final long oldCapacity = capacity;
            increaseMemory(maxBucketLength);
            clearOverflowPointers(oldCapacity, ALLOCATION_FACTOR);
        }

        final long bucketAddress = countOfUsedBytes;

        countOfUsedBytes = newUsed;

        setBucketId(bucketAddress, newBucketId);
        setBucketDepth(bucketAddress, newBucketDepth);
        setBucketFillStatus(bucketAddress, 0, BUCKET_HEADER_LENGTH);

        setBucketCount(getBucketCount() + 1);

        return bucketAddress;
    }

    public void relocateBlock(long bucketAddress, int blockOffset, long newBucketAddress)
    {
        final int destBucketFillCount = getBucketFillCount(newBucketAddress);

        if (destBucketFillCount >= maxBucketBlockCount)
        {
            // overflow
            final long overflowBucketAddress = overflow(newBucketAddress);
            relocateBlock(bucketAddress, blockOffset, overflowBucketAddress);
        }
        else
        {
            final long srcBlockAddress = getRealAddress(bucketAddress) + blockOffset;
            final int destBucketLength = getBucketLength(newBucketAddress);
            final long destBlockAddress = getRealAddress(newBucketAddress) + destBucketLength;

            final int blockLength = getBlockLength(bucketAddress, blockOffset);
            final int newBucketLength = destBucketLength + blockLength;

            // copy to new block
            UNSAFE.copyMemory(srcBlockAddress, destBlockAddress, blockLength);
            setBucketFillStatus(newBucketAddress, destBucketFillCount + 1, newBucketLength);

            // remove from this block (compacts this block)
            removeBlockFromBucket(bucketAddress, blockOffset);

            // TODO remove overflow buckets
        }
    }

    private void setBucketFillStatus(long bucketAddress, int newBucketFillCount, int newBucketLength)
    {
        setBucketFillCount(bucketAddress, newBucketFillCount);
        setBucketLength(bucketAddress, newBucketLength);
    }

    public void writeToStream(OutputStream outputStream, byte[] buffer) throws IOException
    {
        for (long offset = 0; offset < countOfUsedBytes; offset += buffer.length)
        {
            final int copyLength = (int) Math.min(buffer.length, countOfUsedBytes - offset);
            UNSAFE.copyMemory(null, getRealAddress(offset), buffer, ARRAY_BASE_OFFSET, copyLength);
            outputStream.write(buffer, 0, copyLength);
        }
    }

    public void readFromStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        countOfUsedBytes = 0;

        long bytesRead;
        while ((bytesRead = inputStream.read(buffer)) > 0)
        {
            if (countOfUsedBytes + bytesRead > capacity)
            {
                increaseMemory(buffer.length);
            }

            UNSAFE.copyMemory(buffer, ARRAY_BASE_OFFSET, null, getRealAddress(countOfUsedBytes), bytesRead);
            countOfUsedBytes += bytesRead;
        }
        recalculateOccupiedBlocks();
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
            newLength = addExact(capacity, multiplyExact(addition, ALLOCATION_FACTOR));
        }
        catch (final ArithmeticException e)
        {
            throw new IllegalStateException("Unable increase map data buffer", e);
        }

        realAddress = UNSAFE.reallocateMemory(realAddress, newLength);
        capacity = newLength;
    }

    private void clearOverflowPointers(long startAddress, int countOfBuckets)
    {
        for (int i = 0; i < countOfBuckets; i++)
        {
            setBucketOverflowPointer(startAddress + i * maxBucketLength, 0);
        }
    }

    private void recalculateOccupiedBlocks()
    {
        occupiedBlocks = 0;
        long bucketAddress = BUCKET_BUFFER_HEADER_LENGTH;
        while (bucketAddress < countOfUsedBytes)
        {
            occupiedBlocks += getBucketFillCount(bucketAddress);
            bucketAddress += maxBucketLength;
        }
    }
}
