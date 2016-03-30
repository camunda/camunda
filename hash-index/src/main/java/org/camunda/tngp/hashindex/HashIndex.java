package org.camunda.tngp.hashindex;

import uk.co.real_logic.agrona.MutableDirectBuffer;

import static uk.co.real_logic.agrona.collections.Hashing.*;

import org.camunda.tngp.hashindex.IndexValueReader.IntValueReader;
import org.camunda.tngp.hashindex.IndexValueReader.LongValueReader;
import org.camunda.tngp.hashindex.IndexValueWriter.IntValueWriter;
import org.camunda.tngp.hashindex.IndexValueWriter.LongValueWriter;

import static org.camunda.tngp.hashindex.HashIndexDescriptor.*;
import static uk.co.real_logic.agrona.BitUtil.*;

/**
 * Simple index data structure with long keys and extensible hashing.
 * Data structure is not threadsafe
 */
public class HashIndex
{
    protected final MutableDirectBuffer indexBuffer;
    protected final MutableDirectBuffer blockBuffer;

    protected final PutVisitor putVisitor = new PutVisitor();
    protected final GetVisitor getVisitor = new GetVisitor();
    protected final SplitVisitor splitVisitor = new SplitVisitor();

    protected final IntValueReader intValueReader = new IntValueReader();
    protected final IntValueWriter intValueWriter = new IntValueWriter();
    protected final LongValueReader longValueReader = new LongValueReader();
    protected final LongValueWriter longValueWriter = new LongValueWriter();

    public HashIndex(MutableDirectBuffer indexBuffer, MutableDirectBuffer dataBuffer)
    {
        this.indexBuffer = indexBuffer;
        this.blockBuffer = dataBuffer;
    }

    public HashIndex(
            MutableDirectBuffer indexBuffer,
            MutableDirectBuffer dataBuffer,
            int indexSize,
            int blockLength,
            int valueLength)
    {
       this(indexBuffer, dataBuffer);

       // init metadata
       indexSize(indexSize);
       blockLength(blockLength);
       recordLength(framedRecordLength(valueLength));

       // create first block
       blockFillCount(0, 0);
       blockId(0, 0);
       blockDepth(0, 0);
       blockCount(1);
    }

    public boolean get(long key, IndexValueReader valueReader)
    {
        final int blockOffset = hashKeyToBlock(key);

        getVisitor.init(valueReader, key);
        scanBlock(blockOffset, getVisitor);

        return getVisitor.wasRecordFound;
    }

    public long getLong(long key, long missingValue)
    {
        longValueReader.theValue = missingValue;
        get(key, longValueReader);
        return longValueReader.theValue;
    }

    public boolean put(long key, long value)
    {
        longValueWriter.theValue = value;
        return put(key, longValueWriter);
    }

    /**
     * Return true if the put updated an existing entry
     */
    public boolean put(long key, IndexValueWriter valueWriter)
    {
        final int blockOffset = hashKeyToBlock(key);

        putVisitor.init(key, valueWriter, blockOffset);
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
                putBlockOffset = hashKeyToBlock(key);
                // calculate put position (after the split, both blocks are compacted)
                putPosition = blockDataOffset(putBlockOffset) + (recordLength * blockFillCount(putBlockOffset));
            }

            blockBuffer.putByte(recordTypeOffset(putPosition), TYPE_RECORD);
            blockBuffer.putLong(recordKeyOffset(putPosition), key);
            valueWriter.writeValue(blockBuffer, recordValueOffset(putPosition), recordValueLength(recordLength));
            incrementBlockFillCount(putBlockOffset);
        }
        return updated;
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
        return indexBuffer.getInt(RECORD_LENGHT_OFFSET);
    }

    private void recordLength(int recordLength)
    {
        indexBuffer.putInt(RECORD_LENGHT_OFFSET, recordLength);
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
     * hashes a key to a block using the index
     */
    private int hashKeyToBlock(long key)
    {
        final int mask = indexSize() -1;
        final int idx = hash(key, mask);
        return indexBuffer.getInt(indexEntryOffset(idx));
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
            final long recordKey = blockBuffer.getLong(recordKeyOffset(scanPos));

            visitorCompleted = visitor.visitRecord(recordType, recordKey, scanPos, recordSize);

            ++recordsVisited;
            scanPos += recordSize;
        }
    }

    interface RecordVisitor
    {
        boolean visitRecord(short recordType, long recordKey, int recordOffset, int recordLength);
    }

    class GetVisitor implements RecordVisitor
    {
        long keyToFind;
        IndexValueReader reader;
        boolean wasRecordFound;

        void init(IndexValueReader reader, long keyToFind)
        {
            this.keyToFind = keyToFind;
            this.reader = reader;
            this.wasRecordFound = false;
        }

        @Override
        public boolean visitRecord(short recordType, long recordKey, int recordOffset, int recordLength)
        {
            if(recordType == TYPE_RECORD && recordKey == keyToFind)
            {
                reader.readValue(blockBuffer, recordValueOffset(recordOffset), recordValueLength(recordLength));
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
        long keyToUpdate;
        IndexValueWriter writer;
        int freeSlot;
        int blockOffset;
        boolean recordUpdated;

        void init(long keyToUpdate, IndexValueWriter writer, int blockOffset)
        {
            this.keyToUpdate =keyToUpdate;
            this.writer = writer;
            this.blockOffset = blockOffset;
            this.freeSlot = blockDataOffset(blockOffset);
            this.recordUpdated = false;
        }

        @Override
        public boolean visitRecord(short recordType, long recordKey, int recordOffset, int recordLength)
        {
            if(recordType == TYPE_RECORD)
            {
                if(recordKey == keyToUpdate)
                {
                    writer.writeValue(blockBuffer, recordValueOffset(recordOffset), recordValueLength(recordLength));
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
        int filledBlockPutOffset;
        int filledBlockFillCount;
        int newBlockPutOffset;
        int newBlockFillCount;
        int splitMask;

        void init(int filledBlockOffset, int newBlockOffset)
        {
            this.filledBlockPutOffset = -1;
            this.filledBlockFillCount = blockFillCount(filledBlockOffset);
            this.newBlockPutOffset = blockDataOffset(newBlockOffset);
            this.newBlockFillCount = 0;
            this.splitMask = 1 << blockDepth(filledBlockOffset) - 1;
        }

        @Override
        public boolean visitRecord(short recordType, long recordKey, int recordOffset, int recordLength)
        {
            if((recordKey & splitMask) == splitMask)
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
