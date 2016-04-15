package org.camunda.tngp.hashindex;

import static org.camunda.tngp.hashindex.HashIndexDescriptor.*;
import static uk.co.real_logic.agrona.BitUtil.*;

import uk.co.real_logic.agrona.MutableDirectBuffer;

/**
 * Simple index data structure using extensible hashing.
 * Data structure is not threadsafe
 */
public class HashIndex<K extends IndexKeyHandler, V extends IndexValueHandler>
{
    protected final MutableDirectBuffer indexBuffer;
    protected final MutableDirectBuffer blockBuffer;

    protected final PutVisitor putVisitor = new PutVisitor();
    protected final GetVisitor getVisitor = new GetVisitor();
    protected final SplitVisitor splitVisitor;

    protected K keyHandler;
    protected V valueHandler;

    public HashIndex(
            Class<K> keyHandlerType,
            Class<V> valueHandlerType,
            MutableDirectBuffer indexBuffer,
            MutableDirectBuffer dataBuffer,
            int indexSize,
            int blockLength,
            int keyLength,
            int valueLength)
    {
        this.indexBuffer = indexBuffer;
        this.blockBuffer = dataBuffer;
        this.splitVisitor = new SplitVisitor(crateKeyHandlerInstance(keyHandlerType, keyLength));
        this.keyHandler = crateKeyHandlerInstance(keyHandlerType, keyLength);
        this.valueHandler = createInstance(valueHandlerType);

        // init metadata
        indexSize(indexSize);
        blockLength(blockLength);
        recordKeyLength(keyLength);
        recordValueLength(valueLength);

        // create first block
        blockFillCount(0, 0);
        blockId(0, 0);
        blockDepth(0, 0);
        blockCount(1);
    }

    private K crateKeyHandlerInstance(Class<K> keyHandlerType, int keyLength)
    {
        final K keyHandler = createInstance(keyHandlerType);
        keyHandler.setKeyLength(keyLength);
        return keyHandler;
    }

    private static <T> T createInstance(final Class<T> type){
        try
        {
            return type.newInstance();
        }
        catch (InstantiationException | IllegalAccessException e)
        {
            throw new RuntimeException("Could not instantiate "+type, e);
        }
    }

    protected boolean put()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final int blockOffset = blockForHashCode(keyHashCode);

        putVisitor.init(keyHandler, valueHandler, blockOffset);
        scanBlock(blockOffset, putVisitor);

        final boolean updated = putVisitor.recordUpdated;

        if(!updated)
        {
            final int recordLength = recordLength();
            int putPosition = putVisitor.freeSlot;
            int putBlockOffset = blockOffset;

            while(putBlockOffset + blockLength() < putPosition + recordLength)
            {
                // block is filled
                splitBlock(putBlockOffset);
                putBlockOffset = blockForHashCode(keyHashCode);
                // calculate put position (after the split, both blocks are compacted)
                putPosition = blockDataOffset(putBlockOffset) + (recordLength * blockFillCount(putBlockOffset));
            }

            blockBuffer.putByte(recordTypeOffset(putPosition), TYPE_RECORD);
            keyHandler.writeKey(blockBuffer, recordKeyOffset(putPosition));
            valueHandler.writeValue(blockBuffer, recordValueOffset(putPosition, recordKeyLength()), recordValueLength());
            incrementBlockFillCount(putBlockOffset);
        }
        return updated;
    }

    protected boolean get()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final int blockOffset = blockForHashCode(keyHashCode);

        getVisitor.init();
        scanBlock(blockOffset, getVisitor);

        return getVisitor.wasRecordFound;
    }

    private int blockForHashCode(int keyHashCode)
    {

        final int mask = indexSize() -1;
        return indexBuffer.getInt(indexEntryOffset(keyHashCode & mask));
    }

    public int blockLength()
    {
        return indexBuffer.getInt(BLOCK_LENGTH_OFFSET);
    }

    private void blockLength(int blockLength)
    {
        indexBuffer.putInt(BLOCK_LENGTH_OFFSET, blockLength);
    }

    public int recordLength()
    {
        return framedRecordLength(recordKeyLength(), recordValueLength());
    }

    public int recordValueLength()
    {
        return indexBuffer.getInt(RECORD_VALUE_LENGTH_OFFSET);
    }

    private void recordValueLength(int length)
    {
        indexBuffer.putInt(RECORD_VALUE_LENGTH_OFFSET, length);
    }

    public int recordKeyLength()
    {
        return indexBuffer.getInt(RECORD_KEY_LENGTH_OFFSET);
    }

    private void recordKeyLength(int recordKeyLength)
    {
        indexBuffer.putInt(RECORD_KEY_LENGTH_OFFSET, recordKeyLength);
    }

    public int indexSize()
    {
        return indexBuffer.getInt(INDEX_SIZE_OFFSET);
    }

    private void indexSize(int indexSize)
    {
        indexBuffer.putInt(INDEX_SIZE_OFFSET, indexSize);
    }

    public int blockCount()
    {
        return indexBuffer.getInt(BLOCK_COUNT_OFFSET);
    }

    private void blockCount(int blockCount)
    {
        indexBuffer.putInt(BLOCK_COUNT_OFFSET, blockCount);
    }

    /**
     * splits a block performing the index update and relocation and compaction of records.
     */
    private int splitBlock(int filledBlockOffset)
    {
        final int blockCount = blockCount();
        final int indexSize = indexSize();

        int newBlockOffset = -1;
        if(blockCount < indexSize)
        {
            final int filledBlockId = blockId(filledBlockOffset);
            final int blockDepth = blockDepth(filledBlockOffset);
            final int newBlockId = 1 << blockDepth | filledBlockId;
            final int newBlockDepth = blockDepth + 1;

            // create new block
            newBlockOffset = allocateBlock();
            blockId(newBlockOffset, newBlockId);
            blockDepth(filledBlockOffset, newBlockDepth);
            blockDepth(newBlockOffset, newBlockDepth);
            incrementBlockCount();

            // split filled block
            splitVisitor.init(filledBlockOffset, newBlockOffset);
            scanBlock(filledBlockOffset, splitVisitor);
            blockFillCount(filledBlockOffset, splitVisitor.filledBlockFillCount);
            blockFillCount(newBlockOffset, splitVisitor.newBlockFillCount);

            // update index
            for(int idx = 0; idx < indexSize; idx++)
            {
                final int offset = INDEX_OFFSET + (idx * SIZE_OF_INT);
                if(indexBuffer.getInt(offset) == filledBlockOffset)
                {
                    if((idx & newBlockId) == newBlockId)
                    {
                        indexBuffer.putInt(offset, newBlockOffset);
                    }
                }
            }
        }
        else
        {
            throw new RuntimeException("Index full!!!");
        }

        return newBlockOffset;
    }

    private int allocateBlock()
    {
        int allocatedBlockOffset = 0;

        for (int i = 0; i < indexSize(); i++)
        {
            if(indexBuffer.getInt(indexEntryOffset(i)) == allocatedBlockOffset)
            {
                allocatedBlockOffset += framedBlockSize();
                i = -1;
            }
        }

        return allocatedBlockOffset;
    }

    private void blockFillCount(int blockOffset, int fillCount)
    {
        blockBuffer.putInt(blockFillCountOffset(blockOffset), fillCount);
    }

    private int blockFillCount(int blockOffset)
    {
        return blockBuffer.getInt(blockFillCountOffset(blockOffset));
    }

    private void incrementBlockCount()
    {
        blockCount(blockCount() + 1);
    }

    private int blockId(int blockOffset)
    {
        return blockBuffer.getInt(blockIdOffset(blockOffset));
    }

    private void blockId(int blockOffset, int blockId)
    {
        blockBuffer.putInt(blockIdOffset(blockOffset), blockId);
    }

    private int blockDepth(int blockOffset)
    {
        return blockBuffer.getInt(blockDepthOffset(blockOffset));
    }

    private void blockDepth(int blockOffset, int blockDepth)
    {
        blockBuffer.putInt(blockDepthOffset(blockOffset), blockDepth);
    }

    private int framedBlockSize()
    {
        return blockLength() + BLOCK_DATA_OFFSET;
    }

    private void incrementBlockFillCount(final int blockOffset)
    {
        final int blockFillCountOffset = blockFillCountOffset(blockOffset);
        int fillCount = blockBuffer.getInt(blockFillCountOffset);
        blockBuffer.putInt(blockFillCountOffset, fillCount + 1);
    }

    private int indexEntryOffset(int entryIdx)
    {
        return INDEX_OFFSET + (entryIdx * SIZE_OF_INT);
    }

    // block scanning and visitors /////////////////////////////////////////////

    private void scanBlock(int blockOffset, RecordVisitor visitor)
    {
        final int blockDataOffset = blockDataOffset(blockOffset);
        final int recordSize = recordLength();
        final int scanLimit = blockDataOffset + blockLength();
        final int fillCount = blockBuffer.getInt(blockFillCountOffset(blockOffset));

        boolean visitorCompleted = false;
        int scanPos = blockDataOffset;
        int recordsVisited = 0;

        while (!visitorCompleted && scanPos < scanLimit && recordsVisited < fillCount)
        {
            final short recordType = blockBuffer.getByte(recordTypeOffset(scanPos));

            visitorCompleted = visitor.visitRecord(recordType, blockBuffer, scanPos, recordSize);

            ++recordsVisited;
            scanPos += recordSize;
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
            if(recordType == TYPE_RECORD && keyHandler.keyEquals(buffer, recordKeyOffset(recordOffset)))
            {
                valueHandler.readValue(blockBuffer, recordValueOffset(recordOffset, recordKeyLength()), recordValueLength());
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
        IndexValueHandler writer;
        int freeSlot;
        int blockOffset;
        boolean recordUpdated;

        void init(IndexKeyHandler keyHandler, IndexValueHandler writer, int blockOffset)
        {
            this.keyHandler = keyHandler;
            this.writer = writer;
            this.blockOffset = blockOffset;
            this.freeSlot = blockDataOffset(blockOffset);
            this.recordUpdated = false;
        }

        @Override
        public boolean visitRecord(short recordType, MutableDirectBuffer buffer, int recordOffset, int recordLength)
        {
            if(recordType == TYPE_RECORD)
            {
                if(keyHandler.keyEquals(buffer, recordKeyOffset(recordOffset)))
                {
                    writer.writeValue(blockBuffer, recordValueOffset(recordOffset, recordKeyLength()), recordValueLength());
                    incrementBlockFillCount(blockOffset);
                    recordUpdated = true;
                }
                if(freeSlot == recordOffset)
                {
                    freeSlot += recordLength;
                }
            }
            return recordUpdated;
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

        public SplitVisitor(IndexKeyHandler keyHandler)
        {
            this.keyHandler = keyHandler;
        }

        void init(int filledBlockOffset, int newBlockOffset)
        {
            this.filledBlockPutOffset = -1;
            this.filledBlockFillCount = blockFillCount(filledBlockOffset);
            this.newBlockPutOffset = blockDataOffset(newBlockOffset);
            this.newBlockFillCount = 0;
            this.splitMask = 1 << blockDepth(filledBlockOffset) - 1;
        }

        @Override
        public boolean visitRecord(short recordType, MutableDirectBuffer buffer, int recordOffset, int recordLength)
        {
            keyHandler.readKey(buffer, recordKeyOffset(recordOffset));
            int keyHashCode = keyHandler.keyHashCode();
            if((keyHashCode & splitMask) == splitMask)
            {
                // relocate record to the new block
                blockBuffer.putBytes(newBlockPutOffset, blockBuffer, recordOffset, recordLength);

                newBlockPutOffset += recordLength;
                filledBlockPutOffset = recordOffset;

                ++newBlockFillCount;
                --filledBlockFillCount;
            }
            else
            {
                if(filledBlockPutOffset > 0)
                {
                    // compact existing block
                    blockBuffer.putBytes(filledBlockPutOffset, blockBuffer, recordOffset, recordLength);
                    filledBlockPutOffset = recordOffset;
                    blockBuffer.putByte(recordTypeOffset(recordOffset), TYPE_TOMBSTONE);
                }
            }

            return false;
        }
    }
}
