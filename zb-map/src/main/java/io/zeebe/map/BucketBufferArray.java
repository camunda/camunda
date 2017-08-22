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

import static io.zeebe.map.BucketBufferArrayDescriptor.*;
import static java.lang.Math.addExact;
import static java.lang.Math.multiplyExact;
import static org.agrona.BufferUtil.ARRAY_BASE_OFFSET;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class BucketBufferArray implements AutoCloseable
{
    public static final int ALLOCATION_FACTOR = 32;
    public static final int OVERFLOW_BUCKET_ID = -1;
    private static final long INVALID_ADDRESS = 0;

    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;
    private static final String FAIL_MSG_TO_READ_BUCKET_BUFFER = "Failed to read bucket buffer array, managed to read %d bytes.";

    private final int maxBucketLength;
    private final int maxBucketBlockCount;
    private final int maxKeyLength;
    private final int maxValueLength;
    private final int maxBucketBufferLength;

    private long realAddresses[];
    private long bucketBufferHeaderAddress;

    public BucketBufferArray(int maxBucketBlockCount, int maxKeyLength, int maxValueLength)
    {
        this.maxBucketLength =
            addExact(BUCKET_DATA_OFFSET,
                     multiplyExact(maxBucketBlockCount, BucketBufferArrayDescriptor.getBlockLength(maxKeyLength, maxValueLength)));
        try
        {
            this.maxBucketBufferLength = addExact(BUCKET_BUFFER_HEADER_LENGTH, multiplyExact(ALLOCATION_FACTOR, maxBucketLength));
        }
        catch (ArithmeticException ae)
        {
            throw new IllegalArgumentException("Maximum bucket buffer length exceeds integer maximum value.", ae);
        }

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
        this.realAddresses = new long[ALLOCATION_FACTOR];
        bucketBufferHeaderAddress = UNSAFE.allocateMemory(MAIN_BUCKET_BUFFER_HEADER_LEN);

        setBucketBufferCount(0);
        setBucketCount(0);
        setBlockCount(0);

        allocateNewBucketBuffer(0);
    }

    // BUCKET BUFFER ARRAY ///////////////////////////////////////////////////////////////////////////

    protected static long getBucketAddress(int bucketBufferId, int bucketOffset)
    {
        long bucketAddress = 0;
        bucketAddress += (long) bucketBufferId << 32;
        bucketAddress += bucketOffset;
        return bucketAddress;
    }

    private long getBlockAddress(long bucketAddress, int blockOffset)
    {
        return getRealAddress(bucketAddress) + blockOffset;
    }

    private long getRealAddress(final long bucketAddress)
    {
        final int bucketBufferId = (int) (bucketAddress >> 32);
        final int bucketOffset = (int) bucketAddress;
        return getRealAddress(bucketBufferId, bucketOffset);
    }

    private long getRealAddress(int bucketBufferId, int offset)
    {
        if (offset < 0 || offset >= maxBucketBufferLength)
        {
            throw new IllegalArgumentException("Can't access " + offset + " max bucket buffer length is: " + maxBucketBufferLength);
        }

        return realAddresses[bucketBufferId] + offset;
    }

    private void setBucketBufferCount(int newBucketBufferCount)
    {
        UNSAFE.putInt(bucketBufferHeaderAddress + MAIN_BUFFER_COUNT_OFFSET, newBucketBufferCount);
    }

    private void setBucketCount(int newBucketCount)
    {
        UNSAFE.putInt(bucketBufferHeaderAddress + MAIN_BUCKET_COUNT_OFFSET, newBucketCount);
    }

    private void setBlockCount(long newBlockCount)
    {
        UNSAFE.putLong(bucketBufferHeaderAddress + MAIN_BLOCK_COUNT_OFFSET, newBlockCount);
    }

    public int getBucketBufferCount()
    {
        return UNSAFE.getInt(bucketBufferHeaderAddress + MAIN_BUFFER_COUNT_OFFSET);
    }

    public int getBucketCount()
    {
        return UNSAFE.getInt(bucketBufferHeaderAddress + MAIN_BUCKET_COUNT_OFFSET);
    }

    public long getBlockCount()
    {
        return UNSAFE.getLong(bucketBufferHeaderAddress + MAIN_BLOCK_COUNT_OFFSET);
    }

    @Override
    public void close()
    {
        UNSAFE.freeMemory(bucketBufferHeaderAddress);
        for (long realAddress : realAddresses)
        {
            if (realAddress != INVALID_ADDRESS)
            {
                UNSAFE.freeMemory(realAddress);
            }
        }
    }

    public int getFirstBucketOffset()
    {
        return BUCKET_BUFFER_HEADER_LENGTH;
    }

    public long getCapacity()
    {
        return getBucketBufferCount() * maxBucketBufferLength;
    }

    protected long getCountOfUsedBytes()
    {
        return getBucketBufferCount() * BUCKET_BUFFER_HEADER_LENGTH + getBucketCount() * maxBucketLength;
    }

    public long size()
    {
        return MAIN_BUCKET_BUFFER_HEADER_LEN + getCountOfUsedBytes();
    }

    public int getMaxBucketBufferLength()
    {
        return maxBucketBufferLength;
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
            return (float) getBlockCount() / (float) (bucketCount * maxBucketBlockCount);
        }
    }

    public int getMaxBucketLength()
    {
        return maxBucketLength;
    }

    // BUCKET BUFFER ///////////////////////////////////////////////////////////////

    public int getBucketCount(int bucketBufferId)
    {
        return UNSAFE.getInt(getRealAddress(bucketBufferId, BUCKET_BUFFER_BUCKET_COUNT_OFFSET));
    }

    private void setBucketCount(int bucketBufferId, int blockCount)
    {
        UNSAFE.putInt(getRealAddress(bucketBufferId, BUCKET_BUFFER_BUCKET_COUNT_OFFSET), blockCount);
    }

    // BUCKET //////////////////////////////////////////////////////////////////////

    public int getBucketFillCount(long bucketAddress)
    {
        return UNSAFE.getInt(getRealAddress(bucketAddress) + BUCKET_FILL_COUNT_OFFSET);
    }

    private void initBucketFillCount(int bucketBufferId, int bucketOffset)
    {
        UNSAFE.putInt(getRealAddress(bucketBufferId, bucketOffset) + BUCKET_FILL_COUNT_OFFSET, 0);
    }

    private void setBucketFillCount(long bucketAddress, int blockFillCount)
    {
        UNSAFE.putInt(getRealAddress(bucketAddress) + BUCKET_FILL_COUNT_OFFSET, blockFillCount);
    }

    public int getBucketLength(long bucketAddress)
    {
        return getBucketFillCount(bucketAddress) * (maxKeyLength + maxValueLength) + BUCKET_HEADER_LENGTH;
    }

    public long getBucketOverflowPointer(long bucketAddress)
    {
        return UNSAFE.getLong(getRealAddress(bucketAddress) + BUCKET_OVERFLOW_POINTER_OFFSET);
    }

    private void clearBucketOverflowPointer(int bucketBufferId, int bucketOffset)
    {
        UNSAFE.putLong(getRealAddress(bucketBufferId, bucketOffset) + BUCKET_OVERFLOW_POINTER_OFFSET, 0L);
    }

    private void setBucketOverflowPointer(long bucketAddress, long overflowPointer)
    {
        UNSAFE.putLong(getRealAddress(bucketAddress) + BUCKET_OVERFLOW_POINTER_OFFSET, overflowPointer);
    }

    public int getBucketOverflowCount(long bucketAddress)
    {
        long address = getBucketOverflowPointer(bucketAddress);
        int count = 0;
        while (address != 0)
        {
            address = getBucketOverflowPointer(address);
            count++;
        }
        return count;
    }

    public int getFirstBlockOffset()
    {
        return BUCKET_DATA_OFFSET;
    }

    // BLOCK ///////////////////////////////////////////////////////////////////////////////////////////

    public int getBlockLength()
    {
        return BucketBufferArrayDescriptor.getBlockLength(maxKeyLength, maxValueLength);
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
        final long valueOffset = getBlockValueOffset(getBlockAddress(bucketAddress, blockOffset), maxKeyLength);
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

            final long blockAddress = getBlockAddress(bucketAddress, blockOffset);

            keyHandler.writeKey(blockAddress + BLOCK_KEY_OFFSET);
            valueHandler.writeValue(getBlockValueOffset(blockAddress, maxKeyLength));

            setBucketFillCount(bucketAddress, bucketFillCount + 1);
            setBlockCount(getBlockCount() + 1);
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
        setBlockCount(getBlockCount() - 1);
    }

    private void removeBlockFromBucket(long bucketAddress, int blockOffset)
    {
        final int blockLength = getBlockLength();
        final int nextBlockOffset = blockOffset + blockLength;

        moveRemainingMemory(bucketAddress, nextBlockOffset, -blockLength);

        setBucketFillCount(bucketAddress, getBucketFillCount(bucketAddress) - 1);
    }

    private void setBucketId(int bucketBufferId, int bucketOffset, int newBlockId)
    {
        UNSAFE.putInt(getRealAddress(bucketBufferId, bucketOffset) + BUCKET_ID_OFFSET, newBlockId);
    }

    public int getBucketId(long bucketAddress)
    {
        return UNSAFE.getInt(getRealAddress(bucketAddress) + BUCKET_ID_OFFSET);
    }

    private void setBucketDepth(int bucketBufferId, int bucketOffset, int newBlockDepth)
    {
        UNSAFE.putInt(getRealAddress(bucketBufferId, bucketOffset) + BUCKET_DEPTH_OFFSET, newBlockDepth);
    }

    protected void setBucketDepth(long bucketAddress, int newBlockDepth)
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
        int bucketBufferId = getBucketBufferCount() - 1;
        int bucketCountInBucketBuffer = getBucketCount(bucketBufferId);

        if (bucketCountInBucketBuffer >= ALLOCATION_FACTOR)
        {
            allocateNewBucketBuffer(++bucketBufferId);
            bucketCountInBucketBuffer = 0;
        }

        final int bucketOffset = BUCKET_BUFFER_HEADER_LENGTH + bucketCountInBucketBuffer * maxBucketLength;
        final long bucketAddress = getBucketAddress(bucketBufferId, bucketOffset);


        setBucketId(bucketBufferId, bucketOffset, newBucketId);
        setBucketDepth(bucketBufferId, bucketOffset, newBucketDepth);
        clearBucketOverflowPointer(bucketBufferId, bucketOffset);
        initBucketFillCount(bucketBufferId, bucketOffset);

        setBucketCount(bucketBufferId, bucketCountInBucketBuffer + 1);
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
            final long srcBlockAddress = getBlockAddress(bucketAddress, blockOffset);
            final int destBucketLength = getBucketLength(newBucketAddress);
            final long destBlockAddress = getBlockAddress(newBucketAddress, destBucketLength);

            final int blockLength = getBlockLength();

            // copy to new block
            UNSAFE.copyMemory(srcBlockAddress, destBlockAddress, blockLength);
            setBucketFillCount(newBucketAddress, destBucketFillCount + 1);

            // remove from this block (compacts this block)
            removeBlockFromBucket(bucketAddress, blockOffset);

            // TODO remove overflow buckets
        }
    }

    // BUCKET BUFFER IO ///////////////////////////

    private void write(OutputStream outputStream, byte[] writeBuffer, long sourceAddress, int maxWriteLength) throws IOException
    {
        int remainingBytesToWrite = maxWriteLength;
        while (remainingBytesToWrite > 0)
        {
            final int copyLength = Math.min(writeBuffer.length, remainingBytesToWrite);
            final int offset = maxWriteLength - remainingBytesToWrite;
            UNSAFE.copyMemory(null, sourceAddress + offset, writeBuffer, ARRAY_BASE_OFFSET, copyLength);
            outputStream.write(writeBuffer, 0, copyLength);
            remainingBytesToWrite -= copyLength;
        }
    }

    public void writeToStream(OutputStream outputStream, byte[] writeBuffer) throws IOException
    {
        // write main header
        write(outputStream, writeBuffer, bucketBufferHeaderAddress, MAIN_BUCKET_BUFFER_HEADER_LEN);

        // write data
        final int bucketBufferCount = getBucketBufferCount();
        for (int bucketBufferIndex = 0; bucketBufferIndex < bucketBufferCount - 1; bucketBufferIndex++)
        {
            write(outputStream, writeBuffer, realAddresses[bucketBufferIndex], getMaxBucketBufferLength());
        }

        // last bucket buffer is most of the time not full
        final long countOfUsedBytes = getCountOfUsedBytes();
        final long remainingBytes = countOfUsedBytes - ((bucketBufferCount - 1) * getMaxBucketBufferLength());
        write(outputStream, writeBuffer, realAddresses[bucketBufferCount - 1], (int) remainingBytes);
    }

    private int readInto(InputStream inputStream, byte[] readBuffer, long destinationAddress, int maxReadLength) throws IOException
    {
        int remainingBytes = maxReadLength;
        int readLength = Math.min(readBuffer.length, remainingBytes);
        int bytesRead = 0;

        while (remainingBytes > 0 && bytesRead >= 0)
        {
            bytesRead = inputStream.read(readBuffer, 0, readLength);

            if (bytesRead > 0)
            {
                final int offset = maxReadLength - remainingBytes;
                UNSAFE.copyMemory(readBuffer, ARRAY_BASE_OFFSET, null, destinationAddress + offset, bytesRead);

                remainingBytes -= bytesRead;
                readLength = Math.min(readBuffer.length, remainingBytes);
            }
        }
        return maxReadLength - remainingBytes;
    }

    public void readFromStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        clear();
        long countOfUsedBytes = 0;

        try
        {
            readInto(inputStream, buffer, bucketBufferHeaderAddress, MAIN_BUCKET_BUFFER_HEADER_LEN);

            final int readBucketBufferCount = getBucketBufferCount();
            setBucketBufferCount(1);
            int readBytes = 0;
            for (int i = 0; i < readBucketBufferCount; i++)
            {
                if (readBytes == getMaxBucketBufferLength())
                {
                    allocateNewBucketBuffer(i);
                }
                readBytes = readInto(inputStream, buffer, realAddresses[i], getMaxBucketBufferLength());
                countOfUsedBytes += readBytes;
            }
        }
        catch (IOException ioe)
        {
            final String errorMessage = String.format(FAIL_MSG_TO_READ_BUCKET_BUFFER, countOfUsedBytes);
            clear();
            throw new IOException(errorMessage, ioe);
        }
    }

    // HELPER METHODS ////////////////////////

    private void moveRemainingMemory(long bucketAddress, int srcOffset, int moveBytes)
    {
        final int bucketLength = getBucketLength(bucketAddress);

        if (srcOffset < bucketLength)
        {
            final long srcAddress = getBlockAddress(bucketAddress, srcOffset);
            final int remainingBytes = bucketLength - srcOffset;

            UNSAFE.copyMemory(srcAddress, srcAddress + moveBytes, remainingBytes);
        }
    }

    private void allocateNewBucketBuffer(int newBucketBufferId)
    {
        if (newBucketBufferId >= realAddresses.length)
        {
            final long newAddressTable[] = new long[realAddresses.length * 2];
            System.arraycopy(realAddresses, 0, newAddressTable, 0, realAddresses.length);
            realAddresses = newAddressTable;
        }
        realAddresses[newBucketBufferId] = UNSAFE.allocateMemory(maxBucketBufferLength);
        setBucketCount(newBucketBufferId, 0);
        setBucketBufferCount(getBucketBufferCount() + 1);
    }

    public String toString()
    {
        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < realAddresses.length; i++)
        {
            if (realAddresses[i] != 0)
            {
                final int bucketCount = getBucketCount(i);
                for (int j = 0; j < bucketCount; j++)
                {
                    final int offset = BUCKET_BUFFER_HEADER_LENGTH + j * getMaxBucketLength();
                    builder.append(toString(getBucketAddress(i, offset)))
                           .append("\n");
                }
            }
            else
            {
                break;
            }
        }
        return builder.toString();
    }

    private String toString(long bucketAddress)
    {
        final StringBuilder builder = new StringBuilder();
        int bucketFillCount = getBucketFillCount(bucketAddress);
        long address = getBucketOverflowPointer(bucketAddress);
        int count = 0;
        while (address != 0)
        {
            bucketFillCount += getBucketFillCount(address);
            address = getBucketOverflowPointer(address);
            count++;
        }

        builder.append("Bucket-")
               .append(getBucketId(bucketAddress))
               .append(" contains ")
               .append(getBlockCount() == 0 ? 0 : ((double) bucketFillCount / (double) getBlockCount()) * 100D)
               .append(" % of all blocks")
               .append(":[ Blocks:")
               .append(bucketFillCount)
               .append(" ,Overflow:")
               .append(count)
               .append("]");
        return builder.toString();
    }
}
