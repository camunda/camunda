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

import static io.zeebe.hashindex.HashIndexDescriptor.BLOCK_DATA_OFFSET;

import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.slf4j.Logger;

/**
 * Simple index data structure using extensible hashing.
 * Data structure is not threadsafe
 */
public class HashIndex<K extends IndexKeyHandler, V extends IndexValueHandler>
{
    public static final Logger LOG = Loggers.HASH_INDEX_LOGGER;

    private static final String FINALIZER_WARNING = "HashIndex is being garbage collected but is not closed.\n" +
            "This means that the object is being de-referenced but the close() method has not been called.\n" +
            "HashIndex allocates memory off the heap which is not reclaimed unless close() is invoked.\n";

    protected final K keyHandler;
    protected final K splitKeyHandler;
    protected final V valueHandler;

    private final HashIndexBuffer indexBuffer;
    private final HashIndexDataBuffer dataBuffer;

    private final int indexSize;
    private final int mask;
    protected final int keyLength;

    protected final AtomicBoolean isClosed = new AtomicBoolean(false);

    public HashIndex(
            Class<K> keyHandlerType,
            Class<V> valueHandlerType,
            int indexSize,
            int maxBlockLength,
            int keyLength)
    {
        indexSize = BitUtil.findNextPositivePowerOfTwo(indexSize);

        this.keyLength = keyLength;
        this.indexSize = indexSize;
        this.mask = this.indexSize - 1;

        this.keyHandler = crateKeyHandlerInstance(keyHandlerType, keyLength);
        this.splitKeyHandler = crateKeyHandlerInstance(keyHandlerType, keyLength);
        this.valueHandler = createInstance(valueHandlerType);

        this.indexBuffer = new HashIndexBuffer(indexSize);
        this.dataBuffer = new HashIndexDataBuffer(indexSize, maxBlockLength, keyLength);

        init();

    }

    private void init()
    {
        final long blockOffset = this.dataBuffer.allocateNewBlock(0, 0);
        for (int idx = 0; idx < indexSize; idx++)
        {
            indexBuffer.setBlockOffset(idx, blockOffset);
        }
    }

    public void close()
    {
        if (isClosed.compareAndSet(false, true))
        {
            CloseHelper.quietClose(indexBuffer);
            CloseHelper.quietClose(dataBuffer);
        }
    }

    @Override
    protected void finalize() throws Throwable
    {
        if (!isClosed.get())
        {
            LOG.error(FINALIZER_WARNING);
        }

    }

    public void clear()
    {
        indexBuffer.clear();
        dataBuffer.clear();

        init();
    }

    private static <K extends IndexKeyHandler> K crateKeyHandlerInstance(Class<K> keyHandlerType, int keyLength)
    {
        final K keyHandler = createInstance(keyHandlerType);
        keyHandler.setKeyLength(keyLength);
        return keyHandler;
    }

    private static <T> T createInstance(final Class<T> type)
    {
        try
        {
            return type.newInstance();
        }
        catch (InstantiationException | IllegalAccessException e)
        {
            throw new RuntimeException("Could not instantiate " + type, e);
        }
    }


    public int blockCount()
    {
        return dataBuffer.getBlockCount();
    }

    protected boolean put()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final int blockId = keyHashCode & mask;

        boolean isUpdated = false;
        boolean isPut = false;
        boolean scanForKey = true;

        while (!isPut && !isUpdated)
        {
            final long blockOffset = indexBuffer.getBlockOffset(blockId);

            if (scanForKey)
            {
                final int blockFillCount = dataBuffer.getBlockFillCount(blockOffset);

                int recordOffset = BLOCK_DATA_OFFSET;
                int recordsVisited = 0;
                boolean keyFound = false;

                while (!keyFound && recordsVisited < blockFillCount)
                {
                    keyFound = dataBuffer.keyEquals(keyHandler, blockOffset, recordOffset);

                    if (keyFound)
                    {
                        isUpdated = dataBuffer.updateValue(valueHandler, blockOffset, recordOffset);
                    }

                    recordOffset += dataBuffer.getRecordLength(blockOffset, recordOffset);
                    recordsVisited++;
                }

                scanForKey = keyFound;

                if (keyFound && !isUpdated)
                {
                    // key found but could not be updated since length of new value is greater than length of old value
                    // and block is filled. Need to split to make room
                    splitBlock(blockOffset);
                }
            }
            else
            {
                isPut = dataBuffer.addRecord(blockOffset, keyHandler, valueHandler);

                if (!isPut)
                {
                    splitBlock(blockOffset);
                }
            }
        }

        return isUpdated;
    }

    protected boolean get()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final int blockId = keyHashCode & mask;
        final long blockOffset = indexBuffer.getBlockOffset(blockId);

        final int blockFillCount = dataBuffer.getBlockFillCount(blockOffset);
        int recordOffset = BLOCK_DATA_OFFSET;
        int recordsVisited = 0;
        boolean keyFound = false;

        while (!keyFound && recordsVisited < blockFillCount)
        {
            keyFound = dataBuffer.keyEquals(keyHandler, blockOffset, recordOffset);

            if (keyFound)
            {
                dataBuffer.readValue(valueHandler, blockOffset, recordOffset);
            }

            recordOffset += dataBuffer.getRecordLength(blockOffset, recordOffset);
            recordsVisited++;
        }

        return keyFound;
    }

    protected boolean remove()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final int blockId = keyHashCode & mask;
        final long blockOffset = indexBuffer.getBlockOffset(blockId);

        final int blockFillCount = dataBuffer.getBlockFillCount(blockOffset);
        int recordOffset = BLOCK_DATA_OFFSET;
        int recordsVisited = 0;
        boolean keyFound = false;

        while (!keyFound && recordsVisited < blockFillCount)
        {
            keyFound = dataBuffer.keyEquals(keyHandler, blockOffset, recordOffset);

            if (keyFound)
            {
                dataBuffer.readValue(valueHandler, blockOffset, recordOffset);
                dataBuffer.removeRecord(blockOffset, recordOffset);
            }

            recordOffset += dataBuffer.getRecordLength(blockOffset, recordOffset);
            recordsVisited++;
        }

        return keyFound;
    }

    /**
     * splits a block performing the index update and relocation and compaction of records.
     */
    private void splitBlock(long filledBlockOffset)
    {
        final int filledBlockId = dataBuffer.getBlockId(filledBlockOffset);
        final int blockDepth = dataBuffer.getBlockDepth(filledBlockOffset);

        // calculate new ids and depths
        final int newBlockId = 1 << blockDepth | filledBlockId;
        final int newBlockDepth = blockDepth + 1;

        if (newBlockId >= indexSize)
        {
            // TODO: implement overflow!
            throw new RuntimeException("Index Full. Cannot create new block with id " + newBlockId);
        }

        // update filled block depth
        dataBuffer.setBlockDepth(filledBlockOffset, newBlockDepth);

        // create new block
        final long newBlockOffset = dataBuffer.allocateNewBlock(newBlockId, newBlockDepth);

        // distribute entries into correct blocks
        distributeEntries(filledBlockOffset, newBlockOffset, blockDepth);

        // update index
        final int indexDiff = 1 << newBlockDepth;
        for (int i = newBlockId; i < indexSize; i += indexDiff)
        {
            indexBuffer.setBlockOffset(i, newBlockOffset);
        }
    }

    private void distributeEntries(long filledBlockOffset, long newBlockOffset, int blockDepth)
    {
        final int blockFillCount = dataBuffer.getBlockFillCount(filledBlockOffset);
        final int splitMask = 1 << blockDepth;

        int recordOffset = BLOCK_DATA_OFFSET;
        int recordsVisited = 0;

        while (recordsVisited < blockFillCount)
        {
            final int recordLength = dataBuffer.getRecordLength(filledBlockOffset, recordOffset);

            dataBuffer.readKey(splitKeyHandler, filledBlockOffset, recordOffset);
            final int keyHashCode = splitKeyHandler.keyHashCode();

            if ((keyHashCode & splitMask) == splitMask)
            {
                dataBuffer.relocateRecord(filledBlockOffset, recordOffset, recordLength, newBlockOffset);
            }
            else
            {
                recordOffset += recordLength;
            }

            recordsVisited++;
        }
    }

    public HashIndexDataBuffer getDataBuffer()
    {
        return dataBuffer;
    }

    public HashIndexBuffer getIndexBuffer()
    {
        return indexBuffer;
    }
}
