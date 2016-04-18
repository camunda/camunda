package org.camunda.tngp.hashindex;

import static uk.co.real_logic.agrona.BitUtil.*;

import uk.co.real_logic.agrona.MutableDirectBuffer;


/**
 * The index has 2 Buffers: the "index buffer" and the "block buffer".
 *
 * Index Buffer layout
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                          BLOCK LENGTH                         |
 *  +---------------------------------------------------------------+
 *  |                        RECORD KEY LENGTH                      |
 *  +---------------------------------------------------------------+
 *  |                       RECORD VALUE LENGTH                     |
 *  +---------------------------------------------------------------+
 *  |                           INDEX SIZE                          |
 *  +---------------------------------------------------------------+
 *  |                           BLOCK COUNT                         |
 *  +---------------------------------------------------------------+
 *  |                                                               |
 *  |                           INDEX DATA                         ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 * Explanation:
 *
 * <ul>
 * <li>Block Length: length of a data block in bytes (immutable)</li>
 * <li>Record Key Length: length of a record key in bytes (immutable)</li>
 * <li>Record Value Length: length of a record value in bytes (immutable)</li>
 * <li>Size: the size of the index in bytes. Must be a power of 2.
 * Controls how many index entries there are</li>
 * <li>Block Count: number of used blocks</li>
 * </ul>
 *
 * Block Buffer layout
 *
 * <pre>
 *  +----------------------------+
 *  |           Block 0          |
 *  +----------------------------+
 *  |             ...            |
 *  +----------------------------+
 *  |           Block n          |
 *  +----------------------------+
 * </pre>
 *
 * Each block has the following layout
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                           FILL COUNT                          |
 *  +---------------------------------------------------------------+
 *  |                            BLOCK ID                           |
 *  +---------------------------------------------------------------+
 *  |                           Block DEPTH                         |
 *  +---------------------------------------------------------------+
 *  |                                                               |
 *  |                           RECORD DATA                        ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 * The record data contains the records
 *
 * Each record has the following layout
 *
 *  * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |  Record Type |                                               ...
 *  +---------------               Key                              |
 * ...                                                              |
 *  +---------------------------------------------------------------+
 *  |                             Value                            ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 *
 */
public class HashIndexDescriptor
{
    public static final int BLOCK_LENGTH_OFFSET;
    public static final int RECORD_KEY_LENGTH_OFFSET;
    public static final int RECORD_VALUE_LENGTH_OFFSET;
    public static final int INDEX_SIZE_OFFSET;
    public static final int BLOCK_COUNT_OFFSET;
    public static final int INDEX_OFFSET;

    public static final int BLOCK_FILL_COUNT_OFFSET;
    public static final int BLOCK_ID_OFFSET;
    public static final int BLOCK_DEPTH_OFFSET;
    public static final int BLOCK_DATA_OFFSET;

    public static final int RECORD_TYPE_OFFSET;
    public static final int RECORD_KEY_OFFSET;

    public static final byte TYPE_RECORD = 1;
    public static final byte TYPE_TOMBSTONE = 2;

    static
    {
        int offset = 0;

        BLOCK_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        RECORD_KEY_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        RECORD_VALUE_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        INDEX_SIZE_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_COUNT_OFFSET = offset;
        offset += SIZE_OF_INT;

        INDEX_OFFSET = offset;

        offset = 0;

        BLOCK_FILL_COUNT_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_DEPTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_DATA_OFFSET = offset;

        offset = 0;

        RECORD_TYPE_OFFSET = offset;
        offset += SIZE_OF_BYTE;

        RECORD_KEY_OFFSET = offset;
    }

    public static int recordTypeOffset(int dataEntryOffset)
    {
        return dataEntryOffset + RECORD_TYPE_OFFSET;
    }

    public static int framedRecordLength(int keyLength, int valueLength)
    {
        return RECORD_KEY_OFFSET + keyLength + valueLength;
    }

    public static int recordValueOffset(int entryOffset, int keyLength)
    {
        return recordKeyOffset(entryOffset) + keyLength;
    }

    public static int recordKeyOffset(int entryOffset)
    {
        return entryOffset + RECORD_KEY_OFFSET;
    }

    public static int requiredIndexBufferSize(int indexSize)
    {
        return (indexSize * SIZE_OF_LONG) + INDEX_OFFSET;
    }

    public static void blockFillCount(MutableDirectBuffer buffer, int fillCount)
    {
        buffer.putInt(BLOCK_FILL_COUNT_OFFSET, fillCount);
    }

    public static int blockFillCount(MutableDirectBuffer buffer)
    {
        return buffer.getInt(BLOCK_FILL_COUNT_OFFSET);
    }

    public static int blockId(MutableDirectBuffer buffer)
    {
        return buffer.getInt(BLOCK_ID_OFFSET);
    }

    public static void blockId(MutableDirectBuffer buffer, int blockId)
    {
        buffer.putInt(BLOCK_ID_OFFSET, blockId);
    }

    public static int blockDepth(MutableDirectBuffer buffer)
    {
        return buffer.getInt(BLOCK_DEPTH_OFFSET);
    }

    public static void blockDepth(MutableDirectBuffer buffer, int blockDepth)
    {
        buffer.putInt(BLOCK_DEPTH_OFFSET, blockDepth);
    }

    public static void incrementBlockFillCount(MutableDirectBuffer buffer)
    {
        blockFillCount(buffer, blockFillCount(buffer) + 1);
    }

    public static void decrementBlockFillCount(MutableDirectBuffer buffer)
    {
        blockFillCount(buffer, blockFillCount(buffer) + 1);
    }

    public static int indexEntryOffset(int entryIdx)
    {
        return INDEX_OFFSET + (entryIdx * SIZE_OF_LONG);
    }
}
