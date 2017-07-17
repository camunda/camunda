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

import static io.zeebe.hashindex.HashIndexDescriptor.*;
import static java.lang.Math.addExact;
import static java.lang.Math.multiplyExact;
import static org.agrona.BufferUtil.ARRAY_BASE_OFFSET;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class HashIndexDataBuffer implements AutoCloseable
{
    public static final int ALLOCATION_FACTOR = 32;

    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    private final int maxBlockLength;
    private final int keyLength;

    private long addr;
    private long length;
    private long used;

    public HashIndexDataBuffer(int maxBlockLength, int keyLength)
    {
        this.maxBlockLength = maxBlockLength;
        this.keyLength = keyLength;

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
            length = addExact(BLOCK_BUFFER_HEADER_LENGTH, multiplyExact(maxBlockLength, ALLOCATION_FACTOR));
        }
        catch (final ArithmeticException e)
        {
            throw new IllegalStateException("Unable to allocate index data buffer", e);
        }

        used = BLOCK_BUFFER_HEADER_LENGTH;

        addr = UNSAFE.allocateMemory(length);

        UNSAFE.setMemory(addr, length, (byte) 0);
    }

    @Override
    public void close()
    {
        UNSAFE.freeMemory(addr);
    }

    public long getLength()
    {
        return length;
    }

    private long getAddress(final long offset)
    {
        return addr + offset;
    }

    public int getBlockCount()
    {
        return UNSAFE.getInt(getAddress(BLOCK_COUNT_OFFSET));
    }

    public void setBlockCount(int blockCount)
    {
        UNSAFE.putInt(getAddress(BLOCK_COUNT_OFFSET), blockCount);
    }

    public int getBlockFillCount(long blockOffset)
    {
        return UNSAFE.getInt(getAddress(blockOffset) + BLOCK_FILL_COUNT_OFFSET);
    }

    public void setBlockFillCount(long blockOffset, int blockFillCount)
    {
        UNSAFE.putInt(getAddress(blockOffset) + BLOCK_FILL_COUNT_OFFSET, blockFillCount);
    }

    public int getBlockLength(long blockOffset)
    {
        return UNSAFE.getInt(getAddress(blockOffset) + BLOCK_LENGTH_OFFSET);
    }

    private void setBlockLength(long blockOffset, int newBlockLength)
    {
        UNSAFE.putInt(getAddress(blockOffset) + BLOCK_LENGTH_OFFSET, newBlockLength);
    }

    public int getRecordLength(long blockOffset, int recordOffset)
    {
        final int valueLength = getRecordValueLength(blockOffset, recordOffset);
        return HashIndexDescriptor.getRecordLength(keyLength, valueLength);
    }

    public int getRecordValueLength(long blockOffset, int recordOffset)
    {
        return UNSAFE.getInt(getAddress(blockOffset) + recordOffset + RECORD_VALUE_LENGTH_OFFSET);
    }

    public void setRecordValueLength(long blockOffset, int recordOffset, int valueLength)
    {
        UNSAFE.putInt(getAddress(blockOffset) + recordOffset + RECORD_VALUE_LENGTH_OFFSET, valueLength);
    }

    public boolean keyEquals(IndexKeyHandler keyHandler, long blockOffset, int recordOffset)
    {
        return keyHandler.keyEquals(getAddress(blockOffset) + recordOffset + RECORD_KEY_OFFSET);
    }

    public void readKey(IndexKeyHandler keyHandler, long blockOffset, int recordOffset)
    {
        keyHandler.readKey(getAddress(blockOffset) + recordOffset + RECORD_KEY_OFFSET);
    }

    public void readValue(IndexValueHandler valueHandler, long blockOffset, int recordOffset)
    {
        final long valueOffset = getRecordValueOffset(getAddress(blockOffset) + recordOffset, keyLength);
        final int valueLength = getRecordValueLength(blockOffset, recordOffset);

        valueHandler.readValue(valueOffset, valueLength);
    }

    public boolean updateValue(IndexValueHandler valueHandler, long blockOffset, int recordOffset)
    {
        final int currBlockLength = getBlockLength(blockOffset);
        final int currValueLength = getRecordValueLength(blockOffset, recordOffset);

        final int diff = valueHandler.getValueLength() - currValueLength;
        final int newBlockLength = currBlockLength + diff;

        final boolean canUpdate = newBlockLength <= maxBlockLength;

        if (canUpdate)
        {
            final int currRecordLength = getRecordLength(blockOffset, recordOffset);
            final int nextRecordOffset = recordOffset + currRecordLength;

            moveRemainingMemory(blockOffset, nextRecordOffset, diff);

            final long recordAddress = getAddress(blockOffset) + recordOffset;

            setRecordValueLength(blockOffset, recordOffset, valueHandler.getValueLength());
            valueHandler.writeValue(getRecordValueOffset(recordAddress, keyLength));

            setBlockLength(blockOffset, newBlockLength);
        }

        return canUpdate;
    }

    public boolean addRecord(long blockOffset, IndexKeyHandler keyHandler, IndexValueHandler valueHandler)
    {
        final int recordOffset = getBlockLength(blockOffset);
        final int recordLength = HashIndexDescriptor.getRecordLength(keyHandler.getKeyLength(), valueHandler.getValueLength());
        final int newBlockLength = recordOffset + recordLength;

        final boolean canAddRecord = newBlockLength <= maxBlockLength;

        if (canAddRecord)
        {
            final long recordAddress = getAddress(blockOffset) + recordOffset;

            setRecordValueLength(blockOffset, recordOffset, valueHandler.getValueLength());
            keyHandler.writeKey(recordAddress + RECORD_KEY_OFFSET);
            valueHandler.writeValue(getRecordValueOffset(recordAddress, keyLength));

            setBlockLength(blockOffset, newBlockLength);
            setBlockFillCount(blockOffset, getBlockFillCount(blockOffset) + 1);
        }

        return canAddRecord;
    }

    public void removeRecord(long blockOffset, int recordOffset)
    {
        final int recordLength = getRecordLength(blockOffset, recordOffset);
        final int nextRecordOffset = recordOffset + recordLength;

        moveRemainingMemory(blockOffset, nextRecordOffset, -recordLength);

        setBlockFillCount(blockOffset, getBlockFillCount(blockOffset) - 1);
        setBlockLength(blockOffset, getBlockLength(blockOffset) - recordLength);
    }

    public void setBlockId(long blockOffset, int newBlockId)
    {
        UNSAFE.putInt(getAddress(blockOffset) + BLOCK_ID_OFFSET, newBlockId);
    }

    public int getBlockId(long blockOffset)
    {
        return UNSAFE.getInt(getAddress(blockOffset) + BLOCK_ID_OFFSET);
    }

    public void setBlockDepth(long blockOffset, int newBlockDepth)
    {
        UNSAFE.putInt(getAddress(blockOffset) + BLOCK_DEPTH_OFFSET, newBlockDepth);
    }

    public int getBlockDepth(long blockOffset)
    {
        return UNSAFE.getInt(getAddress(blockOffset) + BLOCK_DEPTH_OFFSET);
    }

    public long allocateNewBlock(int newBlockId, int newBlockDepth)
    {
        final long newUsed = used + maxBlockLength;

        if (newUsed > length)
        {
            increaseMemory(maxBlockLength);
        }

        final long blockOffset = used;

        used = newUsed;

        setBlockId(blockOffset, newBlockId);
        setBlockDepth(blockOffset, newBlockDepth);
        setBlockFillCount(blockOffset, 0);
        setBlockLength(blockOffset, BLOCK_HEADER_LENGTH);

        setBlockCount(getBlockCount() + 1);

        return blockOffset;
    }

    public void relocateRecord(long blockOffset, int recordOffset, int recordLength, long newBlockOffset)
    {
        final long srcRecordAddr = getAddress(blockOffset) + recordOffset;

        final int destBlockLength = getBlockLength(newBlockOffset);
        final long destRecordAddr = getAddress(newBlockOffset) + destBlockLength;

        final int newBlockLength = destBlockLength + recordLength;

        if (newBlockLength > maxBlockLength)
        {
            throw new IllegalStateException("Unable to move record to full block (" + newBlockLength + " > " + maxBlockLength + ")");
        }

        // copy to new block
        UNSAFE.copyMemory(srcRecordAddr, destRecordAddr, recordLength);
        setBlockFillCount(newBlockOffset, getBlockFillCount(newBlockOffset) + 1);
        setBlockLength(newBlockOffset, newBlockLength);

        // remove from this block (compacts this block)
        removeRecord(blockOffset, recordOffset);
    }

    public void writeToStream(OutputStream outputStream, byte[] buffer) throws IOException
    {
        for (long offset = 0; offset < length; offset += buffer.length)
        {
            final int copyLength = (int) Math.min(buffer.length, length - offset);
            UNSAFE.copyMemory(null, getAddress(offset), buffer, ARRAY_BASE_OFFSET, copyLength);
            outputStream.write(buffer, 0, copyLength);
        }
    }

    public void readFromStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        used = 0;
        length = 0;

        long bytesRead;
        while ((bytesRead = inputStream.read(buffer)) > 0)
        {
            if (used + bytesRead > length)
            {
                increaseMemory(buffer.length);
            }

            UNSAFE.copyMemory(buffer, ARRAY_BASE_OFFSET, null, getAddress(used), bytesRead);
            used += bytesRead;
        }
    }

    private void moveRemainingMemory(long blockOffset, int srcOffset, int moveBytes)
    {
        final int blockLength = getBlockLength(blockOffset);
        final int newBlockLength = blockLength + moveBytes;

        if (srcOffset < blockLength)
        {
            if (newBlockLength > maxBlockLength)
            {
                throw new IllegalStateException("Cannot move bytes out of max block length (" + newBlockLength + " > " + maxBlockLength + ")");
            }

            final long srcAddress = getAddress(blockOffset) + srcOffset;
            final int remainingBytes = blockLength - srcOffset;

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
            throw new IllegalStateException("Unable increase index data buffer", e);
        }

        addr = UNSAFE.reallocateMemory(addr, newLength);
        length = newLength;
    }

}
