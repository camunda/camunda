package org.camunda.tngp.hashindex;

import static org.camunda.tngp.hashindex.HashIndexDescriptor.*;

import org.camunda.tngp.hashindex.store.IndexStore;

import uk.co.real_logic.agrona.MutableDirectBuffer;

/**
 * Simple index data structure using extensible hashing.
 * Data structure is not threadsafe
 */
public class HashIndex<K extends IndexKeyHandler, V extends IndexValueHandler>
{
    protected final IndexStore indexStore;

    protected final LoadedBuffer loadedIndexBuffer;
    protected final LoadedBuffer loadedBlockBuffer;
    protected final LoadedBuffer loadedSplitWorkBuffer;

    protected final PutVisitor putVisitor = new PutVisitor();
    protected final GetVisitor getVisitor = new GetVisitor();
    protected final RemoveVisitor removeVisitor = new RemoveVisitor();
    protected final SplitVisitor splitVisitor;

    protected K keyHandler;
    protected V valueHandler;

    /**
     * Restore an existing index from the index store
     */
    public HashIndex(
            IndexStore indexStore,
            Class<K> keyHandlerType,
            Class<V> valueHandlerType)
    {
        this.indexStore = indexStore;
        this.loadedSplitWorkBuffer = new LoadedBuffer(indexStore, true);
        this.loadedBlockBuffer = new LoadedBuffer(indexStore, true);

        this.loadedIndexBuffer = new LoadedBuffer(indexStore, true, 0, INDEX_OFFSET);
        this.loadedIndexBuffer.load(0, requiredIndexBufferSize(indexSize()));

        final int recordKeyLength = recordKeyLength();

        this.splitVisitor = new SplitVisitor(crateKeyHandlerInstance(keyHandlerType, recordKeyLength));
        this.keyHandler = crateKeyHandlerInstance(keyHandlerType, recordKeyLength);
        this.valueHandler = createInstance(valueHandlerType);
    }

    /**
     * Create a new index
     */
    public HashIndex(
            IndexStore indexStore,
            Class<K> keyHandlerType,
            Class<V> valueHandlerType,
            int indexSize,
            int blockLength,
            int keyLength,
            int valueLength)
    {
        this.indexStore = indexStore;
        this.splitVisitor = new SplitVisitor(crateKeyHandlerInstance(keyHandlerType, keyLength));
        this.keyHandler = crateKeyHandlerInstance(keyHandlerType, keyLength);
        this.valueHandler = createInstance(valueHandlerType);
        this.loadedSplitWorkBuffer = new LoadedBuffer(indexStore, true);

        final int indexBufferSize = requiredIndexBufferSize(indexSize);
        indexStore.allocate(indexBufferSize);
        this.loadedIndexBuffer = new LoadedBuffer(indexStore, true, 0, indexBufferSize);

        // init metadata
        blockLength(blockLength);
        recordKeyLength(keyLength);
        recordValueLength(valueLength);
        indexSize(indexSize);

        // allocate and create first block
        final long firstBlockOffset = indexStore.allocate(blockLength);
        this.loadedBlockBuffer = new LoadedBuffer(indexStore, true, firstBlockOffset, blockLength);
        final MutableDirectBuffer firstBlockBuffer = loadedBlockBuffer.getBuffer();
        blockFillCount(firstBlockBuffer, 0);
        blockId(firstBlockBuffer, 0);
        blockDepth(firstBlockBuffer, 0);

        // update index
        blockCount(1);
        for (int i = 0; i < indexSize; i++)
        {
            loadedIndexBuffer.getBuffer()
                .putLong(indexEntryOffset(i), firstBlockOffset);
        }

        loadedIndexBuffer.write();
        loadedBlockBuffer.write();
    }

    private K crateKeyHandlerInstance(Class<K> keyHandlerType, int keyLength)
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

    protected boolean put()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final long blockOffset = blockForHashCode(keyHashCode);

        putVisitor.init(keyHandler, valueHandler);
        scanBlock(blockOffset, putVisitor);

        final boolean updated = putVisitor.recordUpdated;

        if (!updated)
        {
            final int recordLength = recordLength();
            int putPosition = putVisitor.freeSlot;
            long putBlockOffset = blockOffset;

            while (blockLength() < putPosition + recordLength)
            {
                // block is filled
                splitBlock(putBlockOffset);
                putBlockOffset = blockForHashCode(keyHashCode);
                // calculate put position (after the split, both blocks are compacted)
                loadedBlockBuffer.ensureLoaded(putBlockOffset, blockLength());
                putPosition = BLOCK_DATA_OFFSET + (recordLength * blockFillCount(loadedBlockBuffer.getBuffer()));
            }

            loadedBlockBuffer.ensureLoaded(putBlockOffset, blockLength());
            final MutableDirectBuffer blockBuffer = loadedBlockBuffer.getBuffer();
            blockBuffer.putByte(recordTypeOffset(putPosition), TYPE_RECORD);
            keyHandler.writeKey(blockBuffer, recordKeyOffset(putPosition));
            valueHandler.writeValue(blockBuffer, recordValueOffset(putPosition, recordKeyLength()), recordValueLength());
            incrementBlockFillCount(blockBuffer);
        }

        loadedBlockBuffer.write();

        return updated;
    }

    protected boolean get()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final long blockOffset = blockForHashCode(keyHashCode);

        getVisitor.init();
        scanBlock(blockOffset, getVisitor);

        return getVisitor.wasRecordFound;
    }

    protected boolean remove()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final long blockOffset = blockForHashCode(keyHashCode);

        removeVisitor.init(keyHandler, valueHandler);
        scanBlock(blockOffset, removeVisitor);

        if (removeVisitor.wasRecordRemoved)
        {
            loadedBlockBuffer.write();
        }

        return removeVisitor.wasRecordRemoved;
    }

    private long blockForHashCode(int keyHashCode)
    {
        final int mask = indexSize() - 1;
        return loadedIndexBuffer.getBuffer().getLong(indexEntryOffset(keyHashCode & mask));
    }

    public int blockLength()
    {
        return loadedIndexBuffer.getBuffer().getInt(BLOCK_LENGTH_OFFSET);
    }

    private void blockLength(int blockLength)
    {
        loadedIndexBuffer.getBuffer().putInt(BLOCK_LENGTH_OFFSET, blockLength);
    }

    public int recordLength()
    {
        return framedRecordLength(recordKeyLength(), recordValueLength());
    }

    public int recordValueLength()
    {
        return loadedIndexBuffer.getBuffer().getInt(RECORD_VALUE_LENGTH_OFFSET);
    }

    private void recordValueLength(int length)
    {
        loadedIndexBuffer.getBuffer().putInt(RECORD_VALUE_LENGTH_OFFSET, length);
    }

    public int recordKeyLength()
    {
        return loadedIndexBuffer.getBuffer().getInt(RECORD_KEY_LENGTH_OFFSET);
    }

    private void recordKeyLength(int recordKeyLength)
    {
        loadedIndexBuffer.getBuffer().putInt(RECORD_KEY_LENGTH_OFFSET, recordKeyLength);
    }

    public int indexSize()
    {
        return loadedIndexBuffer.getBuffer().getInt(INDEX_SIZE_OFFSET);
    }

    private void indexSize(int indexSize)
    {
        loadedIndexBuffer.getBuffer().putInt(INDEX_SIZE_OFFSET, indexSize);
    }

    public int blockCount()
    {
        return loadedIndexBuffer.getBuffer().getInt(BLOCK_COUNT_OFFSET);
    }

    private void blockCount(int blockCount)
    {
        loadedIndexBuffer.getBuffer().putInt(BLOCK_COUNT_OFFSET, blockCount);
    }

    /**
     * splits a block performing the index update and relocation and compaction of records.
     */
    private long splitBlock(long filledBlockOffset)
    {
        final int blockCount = blockCount();
        final int indexSize = indexSize();

        long newBlockOffset = -1;
        if (blockCount < indexSize)
        {
            loadedBlockBuffer.ensureLoaded(filledBlockOffset, blockLength());
            final MutableDirectBuffer filledBlockBuffer = loadedBlockBuffer.getBuffer();

            final int filledBlockId = blockId(filledBlockBuffer);
            final int blockDepth = blockDepth(filledBlockBuffer);
            final int newBlockId = 1 << blockDepth | filledBlockId;
            final int newBlockDepth = blockDepth + 1;

            // create new blocks
            newBlockOffset = allocateBlock();
            loadedSplitWorkBuffer.ensureLoaded(newBlockOffset, blockLength());
            final MutableDirectBuffer splitBuffer = loadedSplitWorkBuffer.getBuffer();
            blockId(splitBuffer, newBlockId);
            blockDepth(filledBlockBuffer, newBlockDepth);
            blockDepth(splitBuffer, newBlockDepth);
            incrementBlockCount();

            // split filled block
            splitVisitor.init();
            scanBlock(filledBlockOffset, splitVisitor);
            blockFillCount(filledBlockBuffer, splitVisitor.filledBlockFillCount);
            blockFillCount(splitBuffer, splitVisitor.newBlockFillCount);

            // update index
            final MutableDirectBuffer indexBuffer = loadedIndexBuffer.getBuffer();
            for (int idx = 0; idx < indexSize; idx++)
            {
                final int offset = indexEntryOffset(idx);
                if (indexBuffer.getLong(offset) == filledBlockOffset)
                {
                    if ((idx & newBlockId) == newBlockId)
                    {
                        indexBuffer.putLong(offset, newBlockOffset);
                    }
                }
            }

            loadedBlockBuffer.write();
            loadedSplitWorkBuffer.write();
            loadedIndexBuffer.write();
        }
        else
        {
            throw new RuntimeException("Index full");
        }

        return newBlockOffset;
    }

    private long allocateBlock()
    {
        return indexStore.allocate(blockLength());
    }

    private void incrementBlockCount()
    {
        blockCount(blockCount() + 1);
    }

    // block scanning and visitors /////////////////////////////////////////////

    private void scanBlock(long blockPosition, RecordVisitor visitor)
    {
        ensureBlockLoaded(blockPosition);

        final MutableDirectBuffer blockBuffer = loadedBlockBuffer.getBuffer();
        final int recordSize = recordLength();
        final int scanLimit = blockLength();
        final int fillCount = blockFillCount(blockBuffer);

        boolean visitorCompleted = false;
        int scanPos = BLOCK_DATA_OFFSET;
        int recordsVisited = 0;

        while (!visitorCompleted && scanPos < scanLimit && recordsVisited < fillCount)
        {
            final short recordType = blockBuffer.getByte(recordTypeOffset(scanPos));

            visitorCompleted = visitor.visitRecord(recordType, blockBuffer, scanPos, recordSize);

            if (recordType == TYPE_RECORD)
            {
                ++recordsVisited;
            }
            scanPos += recordSize;
        }
    }

    private void ensureBlockLoaded(long blockPosition)
    {
        if (loadedBlockBuffer.getPosition() != blockPosition)
        {
            loadedBlockBuffer.load(blockPosition, blockLength());
        }
    }

    interface RecordVisitor
    {
        boolean visitRecord(short recordType, MutableDirectBuffer buffer, int recordOffset, int recordLength);
    }

    class GetVisitor implements RecordVisitor
    {
        boolean wasRecordFound;

        void init()
        {
            this.wasRecordFound = false;
        }

        @Override
        public boolean visitRecord(short recordType, MutableDirectBuffer buffer, int recordOffset, int recordLength)
        {
            if (recordType == TYPE_RECORD && keyHandler.keyEquals(buffer, recordKeyOffset(recordOffset)))
            {
                valueHandler.readValue(buffer, recordValueOffset(recordOffset, recordKeyLength()), recordValueLength());
                wasRecordFound = true;
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    class PutVisitor implements RecordVisitor
    {
        IndexKeyHandler keyHandler;
        IndexValueHandler valueHandler;
        int freeSlot;
        boolean recordUpdated;

        void init(IndexKeyHandler keyHandler, IndexValueHandler valueHandler)
        {
            this.keyHandler = keyHandler;
            this.valueHandler = valueHandler;
            this.freeSlot = BLOCK_DATA_OFFSET;
            this.recordUpdated = false;
        }

        @Override
        public boolean visitRecord(short recordType, MutableDirectBuffer buffer, int recordOffset, int recordLength)
        {
            if (recordType == TYPE_RECORD)
            {
                if (keyHandler.keyEquals(buffer, recordKeyOffset(recordOffset)))
                {
                    valueHandler.writeValue(buffer, recordValueOffset(recordOffset, recordKeyLength()), recordValueLength());
                    incrementBlockFillCount(buffer);
                    recordUpdated = true;
                }
                if (freeSlot == recordOffset)
                {
                    freeSlot += recordLength;
                }
            }
            return recordUpdated;
        }
    }

    class RemoveVisitor implements RecordVisitor
    {

        IndexKeyHandler keyHandler;
        IndexValueHandler valueHandler;
        boolean wasRecordRemoved;

        void init(IndexKeyHandler keyHandler, IndexValueHandler valueHandler)
        {
            this.keyHandler = keyHandler;
            this.valueHandler = valueHandler;
            this.wasRecordRemoved = false;
        }

        @Override
        public boolean visitRecord(short recordType, MutableDirectBuffer buffer, int recordOffset, int recordLength)
        {
            if (recordType == TYPE_RECORD)
            {
                if (recordType == TYPE_RECORD && keyHandler.keyEquals(buffer, recordKeyOffset(recordOffset)))
                {
                    valueHandler.readValue(buffer, recordValueOffset(recordOffset, recordKeyLength()), recordValueLength());
                    buffer.putByte(recordTypeOffset(recordOffset), TYPE_TOMBSTONE);
                    decrementBlockFillCount(buffer);
                    wasRecordRemoved = true;
                    return true;
                }
                else
                {
                    return false;
                }
            }
            return false;
        }
    }

    class SplitVisitor implements RecordVisitor
    {
        final IndexKeyHandler keyHandler;
        int filledBlockPutOffset;
        int filledBlockFillCount;
        int newBlockPutOffset;
        int newBlockFillCount;
        int splitMask;

        SplitVisitor(IndexKeyHandler keyHandler)
        {
            this.keyHandler = keyHandler;
        }

        void init()
        {
            this.filledBlockPutOffset = -1;
            this.filledBlockFillCount = blockFillCount(loadedBlockBuffer.getBuffer());
            this.newBlockPutOffset = BLOCK_DATA_OFFSET;
            this.newBlockFillCount = 0;
            this.splitMask = 1 << blockDepth(loadedBlockBuffer.getBuffer()) - 1;
        }

        @Override
        public boolean visitRecord(short recordType, MutableDirectBuffer buffer, int recordOffset, int recordLength)
        {
            keyHandler.readKey(buffer, recordKeyOffset(recordOffset));
            final int keyHashCode = keyHandler.keyHashCode();
            if ((keyHashCode & splitMask) == splitMask)
            {
                // relocate record to the new block
                loadedSplitWorkBuffer.getBuffer().putBytes(newBlockPutOffset, buffer, recordOffset, recordLength);

                newBlockPutOffset += recordLength;
                filledBlockPutOffset = recordOffset;

                ++newBlockFillCount;
                --filledBlockFillCount;
            }
            else
            {
                if (filledBlockPutOffset > 0)
                {
                    // compact existing block
                    buffer.putBytes(filledBlockPutOffset, buffer, recordOffset, recordLength);
                    filledBlockPutOffset = recordOffset;
                    buffer.putByte(recordTypeOffset(recordOffset), TYPE_TOMBSTONE);
                }
            }

            return false;
        }
    }
}
