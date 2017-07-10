/**
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
import static org.agrona.BufferUtil.ARRAY_BASE_OFFSET;

import java.io.*;

import org.agrona.UnsafeAccess;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class HashIndexDataBuffer implements AutoCloseable
{
    private static final int ALLOCATION_FACTOR = 32;

    private static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;

    private final int maxBlockLength;
    private final int keyLength;

    private long addr = -1;
    private long length;
    private long used;

    public HashIndexDataBuffer(int indexSize, int maxBlockLength, int keyLength)
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
        length = BLOCK_BUFFER_HEADER_LENGTH + maxBlockLength * ALLOCATION_FACTOR;
        used = BLOCK_BUFFER_HEADER_LENGTH;

        addr = UNSAFE.allocateMemory(length);

        UNSAFE.setMemory(addr, length, (byte) 0);
    }

    @Override
    public void close()
    {
        UNSAFE.freeMemory(addr);
    }

    public int getBlockCount()
    {
        return UNSAFE.getInt(addr + BLOCK_COUNT_OFFSET);
    }

    public void setBlockCount(int blockCount)
    {
        UNSAFE.putInt(addr + BLOCK_COUNT_OFFSET, blockCount);
    }

    public int getBlockFillCount(long blockOffset)
    {
        return UNSAFE.getInt(addr + blockOffset + BLOCK_FILL_COUNT_OFFSET);
    }

    public void setBlockFillCount(long blockOffset, int blockFillCount)
    {
        UNSAFE.putInt(addr + blockOffset + BLOCK_FILL_COUNT_OFFSET, blockFillCount);
    }

    public int getBlockLength(long blockOffset)
    {
        return UNSAFE.getInt(addr + blockOffset + BLOCK_LENGTH_OFFSET);
    }

    private void setBlockLength(long blockOffset, int newBlockLength)
    {
        UNSAFE.putInt(addr + blockOffset + BLOCK_LENGTH_OFFSET, newBlockLength);
    }

    public int getRecordLength(long blockOffset, int recordOffset)
    {
        return RECORD_KEY_OFFSET + keyLength + getRecordValueLength(blockOffset, recordOffset);
    }

    public int getRecordValueLength(long blockOffset, int recordOffset)
    {
        return UNSAFE.getInt(addr + blockOffset + recordOffset + RECORD_VALUE_LENGTH_OFFSET);
    }

    public boolean keyEquals(IndexKeyHandler keyHandler, long blockOffset, int recordOffset)
    {
        return keyHandler.keyEquals(addr + blockOffset + recordOffset + RECORD_KEY_OFFSET);
    }

    public void readKey(IndexKeyHandler keyHandler, long blockOffset, int recordOffset)
    {
        keyHandler.readKey(addr + blockOffset + recordOffset + RECORD_KEY_OFFSET);
    }

    public void putKey(IndexKeyHandler keyHandler, long blockOffset, int recordOffset)
    {
        keyHandler.writeKey(addr + blockOffset + recordOffset + RECORD_KEY_OFFSET);
    }

    public void readValue(IndexValueHandler valueHandler, long blockOffset, int recordOffset)
    {
        final long recordAddr = addr + blockOffset + recordOffset;
        final int valueLength = UNSAFE.getInt(recordAddr);

        valueHandler.readValue(recordAddr + RECORD_KEY_OFFSET + keyLength, valueLength);
    }

    public boolean canAddEntry(long blockOffset, int valueLength)
    {
        final int blockLength = getBlockLength(blockOffset);
        final int newEntryLength = RECORD_KEY_OFFSET + keyLength + valueLength;
        return blockLength + newEntryLength < maxBlockLength;
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
            final long recordAddr = addr + blockOffset + recordOffset;

            // move following entries if any
            if (currBlockLength > recordOffset + currRecordLength)
            {
                final long nextRecordAddr = recordAddr + currRecordLength;
                final int nextRecordOffset = recordOffset + currRecordLength;
                final int copyLength = currBlockLength - nextRecordOffset;
                UNSAFE.copyMemory(nextRecordAddr, nextRecordAddr + diff, copyLength);
            }

            final long writeValueAddr = recordAddr + RECORD_KEY_OFFSET + keyLength;
            UNSAFE.putInt(recordAddr, valueHandler.getValueLength());
            valueHandler.writeValue(writeValueAddr);

            setBlockLength(blockOffset, newBlockLength);
        }

        return canUpdate;
    }

    public boolean addRecord(long blockOffset, IndexKeyHandler keyHandler, IndexValueHandler valueHandler)
    {
        final int blockLength = getBlockLength(blockOffset);
        final int newEntryLength = RECORD_KEY_OFFSET + keyHandler.getKeyLength() + valueHandler.getValueLength();
        final int newBlockLength = blockLength + newEntryLength;

        final boolean canAddRecord = newBlockLength <= maxBlockLength;

        if (canAddRecord)
        {
            long writeAddr = addr + blockOffset + blockLength;

            UNSAFE.putInt(writeAddr, valueHandler.getValueLength());
            writeAddr += RECORD_KEY_OFFSET;

            keyHandler.writeKey(writeAddr);
            writeAddr += keyLength;

            valueHandler.writeValue(writeAddr);

            setBlockLength(blockOffset, newBlockLength);
            setBlockFillCount(blockOffset, getBlockFillCount(blockOffset) + 1);
        }

        return canAddRecord;
    }

    public void removeRecord(long blockOffset, int recordOffset)
    {
        final int recordLength = getRecordLength(blockOffset, recordOffset);
        final int currBlockLength = getBlockLength(blockOffset);
        final int newBlockLength = currBlockLength - recordLength;
        final long recordAddr = addr + blockOffset + recordOffset;
        final int nextRecordOffset = recordOffset + recordLength;

        // compact and overwrite with following entries (if any)
        if (currBlockLength > nextRecordOffset)
        {
            final long nextRecordAddr = recordAddr + recordLength;
            final int copyLength = currBlockLength - nextRecordOffset;
            UNSAFE.copyMemory(nextRecordAddr, recordAddr, copyLength);
        }

        setBlockLength(blockOffset, newBlockLength);
        setBlockFillCount(blockOffset, getBlockFillCount(blockOffset) - 1);
    }

    public void setBlockId(long blockOffset, int newBlockId)
    {
        UNSAFE.putInt(addr + blockOffset + BLOCK_ID_OFFSET, newBlockId);
    }

    public int getBlockId(long blockOffset)
    {
        return UNSAFE.getInt(addr + blockOffset + BLOCK_ID_OFFSET);
    }

    public void setBlockDepth(long blockOffset, int newBlockDepth)
    {
        UNSAFE.putInt(addr + blockOffset + BLOCK_DEPTH_OFFSET, newBlockDepth);
    }

    public int getBlockDepth(long blockOffset)
    {
        return UNSAFE.getInt(addr + blockOffset + BLOCK_DEPTH_OFFSET);
    }

    public long allocateNewBlock(int newBlockId, int newBlockDepth)
    {
        final long newUsed = used + maxBlockLength;

        if (newUsed > length)
        {
            final long diff = ALLOCATION_FACTOR * maxBlockLength;
            final long newLength = length + diff;
            addr = UNSAFE.reallocateMemory(addr, newLength);
            length = newLength;
        }

        final long blockOffset = used;

        used = newUsed;

        setBlockId(blockOffset, newBlockId);
        setBlockDepth(blockOffset, newBlockDepth);
        setBlockFillCount(blockOffset, 0);
        setBlockLength(blockOffset, BLOCK_DATA_OFFSET);

        setBlockCount(getBlockCount() + 1);

        return blockOffset;
    }

    public void relocateRecord(long blockOffset, int recordOffset, int recordLength, long newBlockOffset)
    {
        final long srcRecordAddr = addr + blockOffset + recordOffset;

        final int destBlockLength = getBlockLength(newBlockOffset);
        final long destRecordAddr = addr + newBlockOffset + destBlockLength;

        // copy to new block
        UNSAFE.copyMemory(srcRecordAddr, destRecordAddr, recordLength);
        setBlockFillCount(newBlockOffset, getBlockFillCount(newBlockOffset) + 1);
        setBlockLength(newBlockOffset, destBlockLength + recordLength);

        // remove from this block (compacts this block)
        removeRecord(blockOffset, recordOffset);
    }

    public void writeToStream(OutputStream outputStream, byte[] buffer) throws IOException
    {
        for (int offset = 0; offset < length; offset += buffer.length)
        {
            final int copyLength = (int) Math.min(buffer.length, length - offset);
            UNSAFE.copyMemory(null, addr + offset, buffer, ARRAY_BASE_OFFSET, copyLength);
            outputStream.write(buffer, 0, copyLength);
        }
    }

    public void readFromStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        addr = UNSAFE.reallocateMemory(addr, 0);
        used = 0;
        length = 0;

        int bytesRead = 0;
        while ((bytesRead = inputStream.read(buffer)) > 0)
        {
            if (used + bytesRead > length)
            {
                final long diff = ALLOCATION_FACTOR * buffer.length;
                final long newLength = length + diff;
                addr = UNSAFE.reallocateMemory(addr, newLength);
                length = newLength;
            }

            UNSAFE.copyMemory(buffer, ARRAY_BASE_OFFSET, null, addr + used, bytesRead);
            used += bytesRead;
        }
    }
}
