package io.zeebe.hashindex;

import static io.zeebe.hashindex.HashIndexDescriptor.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import io.zeebe.hashindex.buffer.BufferCache;
import io.zeebe.hashindex.buffer.BufferCacheMetrics;
import io.zeebe.hashindex.buffer.LoadedBuffer;
import io.zeebe.hashindex.store.IndexStore;

/**
 * Simple index data structure using extensible hashing.
 * Data structure is not threadsafe
 */
public class HashIndex<K extends IndexKeyHandler, V extends IndexValueHandler>
{
    /**
     * Represents the maximum index size. The index is limited to 2^27, because
     * with the given index size we have to create an buffer of longs. Since
     * the data type long has a size of 8 bytes and the index size has to fit into an integer
     * the 2^27 is the last possible power of two.
     */
    public static final int MAX_INDEX_SIZE = 1 << 27;
    private static final int BUFFER_CACHE_SIZE = Integer.parseInt(System.getProperty("io.zeebe.hashindex.bufferCacheSize", "32"));

    protected final IndexStore indexStore;

    private final BufferCache bufferCache;
    protected LoadedBuffer loadedIndexBuffer;

    protected final PutVisitor putVisitor = new PutVisitor();
    protected final GetVisitor getVisitor = new GetVisitor();
    protected final RemoveVisitor removeVisitor = new RemoveVisitor();
    protected final SplitVisitor splitVisitor;

    protected final K keyHandler;
    protected final V valueHandler;

    // immutable metadata

    protected final int blockLength;
    protected final int indexSize;
    protected final int recordKeyLength;
    protected final int recordValueLength;
    protected final int recordLength;

    // mutable metadata

    protected int blockCount;

    /**
     * Restore an existing index from the index store
     */
    public HashIndex(
            IndexStore indexStore,
            Class<K> keyHandlerType,
            Class<V> valueHandlerType)
    {
        this.indexStore = indexStore;

        final LoadedBuffer indexSizeReadBuffer = new LoadedBuffer(indexStore, INDEX_OFFSET);
        indexSizeReadBuffer.load(0);
        this.indexSize = indexSizeReadBuffer.getBuffer().getInt(INDEX_SIZE_OFFSET);
        this.loadedIndexBuffer = new LoadedBuffer(indexStore, requiredIndexBufferSize(indexSize));
        loadedIndexBuffer.load(0);
        this.blockLength = loadedIndexBuffer.getBuffer().getInt(BLOCK_LENGTH_OFFSET);
        this.recordKeyLength = loadedIndexBuffer.getBuffer().getInt(RECORD_KEY_LENGTH_OFFSET);
        this.recordValueLength = loadedIndexBuffer.getBuffer().getInt(RECORD_VALUE_LENGTH_OFFSET);
        this.recordLength = framedRecordLength(recordKeyLength, recordValueLength);
        this.blockCount = loadedIndexBuffer.getBuffer().getInt(BLOCK_COUNT_OFFSET);

        this.splitVisitor = new SplitVisitor(crateKeyHandlerInstance(keyHandlerType, recordKeyLength));
        this.keyHandler = crateKeyHandlerInstance(keyHandlerType, recordKeyLength);
        this.valueHandler = createInstance(valueHandlerType);

        this.bufferCache = new BufferCache(indexStore, BUFFER_CACHE_SIZE, this.blockLength);
    }

    /**
     * Create a new index
     *
     * @param indexStore the index store which is used to store the index
     * @param keyHandlerType the key handler type, which generates the hash for the key
     * @param valueHandlerType the value handler type
     * @param indexSize the size of the index (max count of bucket's),
     *                  if the size is not a power of two it will be round to the next power of two
     *                  The maximum size is 1 << 27 because this index size is also used to allocate the corresponding buffer
     *                  The index size will be multiplied by the size of long. 1 << 27 is the last power of two which fits
     *                  into an int with the multiplication of the size of long.
     * @param blockSize the block size indicates how many entries on bucket can contain
     * @param keyLength the length of the key
     * @param valueLength the length of each value in the bucket
     */
    public HashIndex(
            IndexStore indexStore,
            Class<K> keyHandlerType,
            Class<V> valueHandlerType,
            int indexSize,
            int blockSize,
            int keyLength,
            int valueLength)
    {
        this.indexStore = indexStore;
        this.splitVisitor = new SplitVisitor(crateKeyHandlerInstance(keyHandlerType, keyLength));
        this.keyHandler = crateKeyHandlerInstance(keyHandlerType, keyLength);
        this.valueHandler = createInstance(valueHandlerType);
        final int blockLength = BLOCK_DATA_OFFSET  + blockSize * framedRecordLength(keyLength, valueLength);

        this.blockLength = blockLength;
        this.indexSize = getIndexSizePowerOfTwo(indexSize);
        this.recordKeyLength = keyLength;
        this.recordValueLength = valueLength;
        this.recordLength = framedRecordLength(keyLength, valueLength);
        this.blockCount = 0;

        this.bufferCache = new BufferCache(indexStore, BUFFER_CACHE_SIZE, this.blockLength);

        init();
    }

    protected int getIndexSizePowerOfTwo(int indexSize)
    {
        if (!BitUtil.isPowerOfTwo(indexSize))
        {
            final int maxPowerOfTwo = MAX_INDEX_SIZE;
            if (indexSize > maxPowerOfTwo)
            {
                indexSize = maxPowerOfTwo;
            }
            else
            {
                indexSize = BitUtil.findNextPositivePowerOfTwo(indexSize);
            }
        }
        return indexSize;
    }

    protected void init()
    {
        final int indexBufferSize = requiredIndexBufferSize(indexSize);
        indexStore.allocate(indexBufferSize);
        this.loadedIndexBuffer = new LoadedBuffer(indexStore, indexBufferSize);
        loadedIndexBuffer.load(0);

        // write metadata
        blockLength(blockLength);
        recordKeyLength(recordKeyLength);
        recordValueLength(recordValueLength);
        indexSize(indexSize);

        // allocate and create first block
        final long firstBlockOffset = indexStore.allocate(blockLength);
        final LoadedBuffer loadedBlockBuffer = bufferCache.getBuffer(firstBlockOffset);

        final MutableDirectBuffer firstBlockBuffer = loadedBlockBuffer.getBuffer();
        blockFillCount(firstBlockBuffer, 0);
        blockId(firstBlockBuffer, 0);
        blockDepth(firstBlockBuffer, 0);

        // update index
        blockCount(1);

        final MutableDirectBuffer indexBuffer = loadedIndexBuffer.getBuffer();

        for (int i = 0; i < indexSize; i++)
        {
            indexBuffer.putLong(indexEntryOffset(i), firstBlockOffset);
        }

        loadedIndexBuffer.write();
        indexStore.flush();
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

    protected boolean put()
    {
        final int keyHashCode = keyHandler.keyHashCode();
        final long blockOffset = blockForHashCode(keyHashCode);

        putVisitor.init(keyHandler, valueHandler);
        scanBlock(blockOffset, putVisitor);

        final boolean updated = putVisitor.recordUpdated;

        if (!updated)
        {
            int putPosition = putVisitor.freeSlot;
            long putBlockOffset = blockOffset;

            while (blockLength < putPosition + recordLength)
            {
                // block is filled
                splitBlock(putBlockOffset);
                putBlockOffset = blockForHashCode(keyHashCode);
                // calculate put position (after the split, both blocks are compacted)
                final LoadedBuffer loadedBlockBuffer = bufferCache.getBuffer(putBlockOffset);
                putPosition = BLOCK_DATA_OFFSET + (recordLength * blockFillCount(loadedBlockBuffer.getBuffer()));
            }

            final LoadedBuffer loadedBlockBuffer = bufferCache.getBuffer(putBlockOffset);
            final MutableDirectBuffer blockBuffer = loadedBlockBuffer.getBuffer();
            blockBuffer.putByte(recordTypeOffset(putPosition), TYPE_RECORD);
            keyHandler.writeKey(blockBuffer, recordKeyOffset(putPosition));
            valueHandler.writeValue(blockBuffer, recordValueOffset(putPosition, recordKeyLength), recordValueLength);
            incrementBlockFillCount(blockBuffer);
        }

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

        return removeVisitor.wasRecordRemoved;
    }

    private long blockForHashCode(int keyHashCode)
    {
        final int mask = indexSize - 1;
        return loadedIndexBuffer.getBuffer().getLong(indexEntryOffset(keyHashCode & mask));
    }

    private void blockLength(int blockLength)
    {
        loadedIndexBuffer.getBuffer().putInt(BLOCK_LENGTH_OFFSET, blockLength);
    }

    private void recordValueLength(int length)
    {
        loadedIndexBuffer.getBuffer().putInt(RECORD_VALUE_LENGTH_OFFSET, length);
    }

    private void recordKeyLength(int recordKeyLength)
    {
        loadedIndexBuffer.getBuffer().putInt(RECORD_KEY_LENGTH_OFFSET, recordKeyLength);
    }

    private void indexSize(int indexSize)
    {
        loadedIndexBuffer.getBuffer().putInt(INDEX_SIZE_OFFSET, indexSize);
    }

    private void blockCount(int blockCount)
    {
        this.blockCount = blockCount;
        loadedIndexBuffer.getBuffer().putInt(BLOCK_COUNT_OFFSET, blockCount);
    }

    public int blockCount()
    {
        return blockCount;
    }

    /**
     * splits a block performing the index update and relocation and compaction of records.
     */
    private long splitBlock(long filledBlockOffset)
    {
        long newBlockOffset = -1;
        final LoadedBuffer loadedBlockBuffer = bufferCache.getBuffer(filledBlockOffset);
        final MutableDirectBuffer filledBlockBuffer = loadedBlockBuffer.getBuffer();

        final int filledBlockId = blockId(filledBlockBuffer);
        final int blockDepth = blockDepth(filledBlockBuffer);
        final int newBlockId = 1 << blockDepth | filledBlockId;
        final int newBlockDepth = blockDepth + 1;

        if (newBlockId >= indexSize)
        {
            throw new RuntimeException("Index Full. Cannot create new block with id " + newBlockId + ". Filled Block keys: " + listKeys(filledBlockOffset) + "\n" + getMetrics());
        }

        // create new blocks
        newBlockOffset = allocateBlock();
        final LoadedBuffer loadedSplitWorkBuffer = bufferCache.getBuffer(newBlockOffset);
        final MutableDirectBuffer splitBuffer = loadedSplitWorkBuffer.getBuffer();
        blockId(splitBuffer, newBlockId);
        blockDepth(filledBlockBuffer, newBlockDepth);
        blockDepth(splitBuffer, newBlockDepth);
        incrementBlockCount();

        // split filled block
        splitVisitor.init(loadedBlockBuffer, loadedSplitWorkBuffer);
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

        return newBlockOffset;
    }

    private String listKeys(long filledBlockOffset)
    {
        final StringBuffer buff = new StringBuffer();

        scanBlock(filledBlockOffset, new RecordVisitor()
        {
            @Override
            public boolean visitRecord(short recordType, MutableDirectBuffer buffer, int recordOffset, int recordLength)
            {
                if (recordType == TYPE_RECORD)
                {
                    keyHandler.readKey(buffer, recordKeyOffset(recordOffset));
                    buff.append(keyHandler.toString());
                    buff.append("(");
                    buff.append(keyHandler.keyHashCode() & (indexSize - 1));
                    buff.append(") ");
                }
                return false;
            }
        });

        return buff.toString();
    }

    private long allocateBlock()
    {
        return indexStore.allocate(blockLength);
    }

    private void incrementBlockCount()
    {
        blockCount(blockCount + 1);
    }

    // block scanning and visitors /////////////////////////////////////////////

    private void scanBlock(long blockPosition, RecordVisitor visitor)
    {
        final int scanLimit = blockLength;
        final LoadedBuffer loadedBlockBuffer = bufferCache.getBuffer(blockPosition);

        final MutableDirectBuffer blockBuffer = loadedBlockBuffer.getBuffer();
        final int fillCount = blockFillCount(blockBuffer);

        boolean visitorCompleted = false;
        int scanPos = BLOCK_DATA_OFFSET;
        int recordsVisited = 0;

        while (!visitorCompleted && scanPos < scanLimit && recordsVisited < fillCount)
        {
            final short recordType = blockBuffer.getByte(recordTypeOffset(scanPos));

            visitorCompleted = visitor.visitRecord(recordType, blockBuffer, scanPos, recordLength);

            if (recordType == TYPE_RECORD)
            {
                ++recordsVisited;
            }
            scanPos += recordLength;
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
                valueHandler.readValue(buffer, recordValueOffset(recordOffset, recordKeyLength), recordValueLength);
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
                    valueHandler.writeValue(buffer, recordValueOffset(recordOffset, recordKeyLength), recordValueLength);
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
                    valueHandler.readValue(buffer, recordValueOffset(recordOffset, recordKeyLength), recordValueLength);
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
        LoadedBuffer loadedSplitWorkBuffer;

        SplitVisitor(IndexKeyHandler keyHandler)
        {
            this.keyHandler = keyHandler;
        }

        void init(LoadedBuffer loadedBlockBuffer, LoadedBuffer loadedSplitWorkBuffer)
        {
            this.loadedSplitWorkBuffer = loadedSplitWorkBuffer;
            this.filledBlockPutOffset = -1;
            this.filledBlockFillCount = blockFillCount(loadedBlockBuffer.getBuffer());
            this.newBlockPutOffset = BLOCK_DATA_OFFSET;
            this.newBlockFillCount = 0;
            this.splitMask = 1 << (blockDepth(loadedBlockBuffer.getBuffer()) - 1);
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
                buffer.putByte(recordTypeOffset(recordOffset), TYPE_TOMBSTONE);

                newBlockPutOffset += recordLength;

                if (filledBlockPutOffset == -1)
                {
                    filledBlockPutOffset = recordOffset;
                }

                ++newBlockFillCount;
                --filledBlockFillCount;
            }
            else
            {
                if (filledBlockPutOffset > 0)
                {
                    // compact existing block
                    buffer.putBytes(filledBlockPutOffset, buffer, recordOffset, recordLength);
                    filledBlockPutOffset += recordLength;
                    buffer.putByte(recordTypeOffset(recordOffset), TYPE_TOMBSTONE);
                }
            }

            return false;
        }
    }

    public void clear()
    {
        bufferCache.flush();
        indexStore.clear();
        init();
    }

    public void reInit()
    {
        loadedIndexBuffer.load(0);
        bufferCache.clear();
        blockCount = loadedIndexBuffer.getBuffer().getInt(BLOCK_COUNT_OFFSET);
    }

    public void flush()
    {
        bufferCache.flush();
        loadedIndexBuffer.write();
        indexStore.flush();
    }

    public BufferCacheMetrics getBufferCacheMetrics()
    {
        return bufferCache;
    }

    @Override
    public String toString()
    {
        return getMetrics();
    }

    private String getMetrics()
    {
        final StringBuilder builder = new StringBuilder();

        builder.append(String.format("%d of %d blocks created (%.3f%% extended)\n", blockCount, indexSize, (((double)100) / indexSize) * blockCount));

        final Map<Long, Double> blockFillPercent = new HashMap<>();
        final MutableDirectBuffer indexBuffer = loadedIndexBuffer.getBuffer();

        int size = 0;

        for (int i = 0; i < indexSize; i++)
        {
            final long blockAddr = indexBuffer.getLong(indexEntryOffset(i));

            if (!blockFillPercent.containsKey(blockAddr))
            {
                final LoadedBuffer blockBuffer = bufferCache.getBuffer(blockAddr);
                final int blockFillCount = blockFillCount(blockBuffer.getBuffer());
                size += blockFillCount;
                blockFillPercent.put(blockAddr, (((double) 100) / (blockLength / recordLength)) * blockFillCount);
            }
        }

        builder.append(String.format("Indexed Keys: %d\n", size));


        final TreeMap<Integer, Integer> blockFillHistogram = new TreeMap<>();

        for (Double fillPercent : blockFillPercent.values())
        {
            final int bucket = (int) (fillPercent / 10);

            blockFillHistogram.compute(bucket, (k, v) -> v == null ? 1 : ++v);
        }

        builder.append("Block Fill Histogram: \n");

        for (int i = 0; i < 10; i++)
        {
            Integer count = blockFillHistogram.get(i);
            if (count == null)
            {
                count = 0;
            }
            builder.append(String.format("%d0%% | %d\n", i, count));
        }

        return builder.toString();
    }
}
