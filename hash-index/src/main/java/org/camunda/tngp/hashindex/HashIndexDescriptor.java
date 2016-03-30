package org.camunda.tngp.hashindex;

import static uk.co.real_logic.agrona.BitUtil.*;


/**
 * The index has 2 Buffer: the "index buffer" and the "block buffer".
 *
 * Index Buffer layout
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                              SIZE                             |
 *  +---------------------------------------------------------------+
 *  |                          BLOCK LENGTH                         |
 *  +---------------------------------------------------------------+
 *  |                          RECORD LENGTH                        |
 *  +---------------------------------------------------------------+
 *  |                           BLOCK COUNT                         |
 *  +---------------------------------------------------------------+
 *  |                                                               |
 *  |                           INDEX DATA                        ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 * Explanation:
 *
 * <ul>
 * <li>Size: the size of the index in bytes. Must be a power of 2.
 * Controls how many index entries there are</li>
 * <li>Block Length: length of a data block in bytes</li>
 * <li>Record Length: length of an individual record in bytes</li>
 * <li>Block Count: numbre of eyxisting blocks</li>
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
 *  |  Record Type |               Key                              |
 *  +---------------------------------------------------------------+
 *  |                              Key                              |
 *  +---------------------------------------------------------------+
 *  |    Key       |               Value                            |
 *  +---------------------------------------------------------------+
 *  |                              Value                           ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 *
 */
public class HashIndexDescriptor
{
    public static final int INDEX_SIZE_OFFSET;
    public static final int BLOCK_LENGTH_OFFSET;
    public static final int RECORD_LENGHT_OFFSET;
    public static final int BLOCK_COUNT_OFFSET;
    public static final int INDEX_OFFSET;

    public static final int BLOCK_FILL_COUNT_OFFSET;
    public static final int BLOCK_ID_OFFSET;
    public static final int BLOCK_DEPTH_OFFSET;
    public static final int BLOCK_DATA_OFFSET;

    public static final int RECORD_TYPE_OFFSET;
    public static final int RECORD_KEY_OFFSET;
    public static final int RECORD_VALUE_OFFSET;

    public static final byte TYPE_RECORD = 1;
    public static final byte TYPE_TOMBSTONE = 2;

    static
    {
        int offset = 0;

        INDEX_SIZE_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        RECORD_LENGHT_OFFSET = offset;
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
        offset += SIZE_OF_LONG;

        RECORD_VALUE_OFFSET = offset;
    }

    public static boolean blockMod(long key, int blockMask)
    {
        return (((int) key)  & (Integer.MAX_VALUE >> Integer.numberOfLeadingZeros(blockMask))) == blockMask;
    }

    public static int blockIdOffset(int blockOffset)
    {
        return blockOffset + BLOCK_ID_OFFSET;
    }

    public static int blockDepthOffset(int blockOffset)
    {
        return blockOffset + BLOCK_DEPTH_OFFSET;
    }

    public static int blockFillCountOffset(int blockOffset)
    {
        return blockOffset + BLOCK_FILL_COUNT_OFFSET;
    }

    public static int blockDataOffset(int blockOffset)
    {
        return blockOffset + BLOCK_DATA_OFFSET;
    }

    public static int recordTypeOffset(int dataEntryOffset)
    {
        return dataEntryOffset + RECORD_TYPE_OFFSET;
    }

    public static int recordValueLength(int recordLength)
    {
        return recordLength - RECORD_VALUE_OFFSET;
    }

    public static int framedRecordLength(int valueLength)
    {
        return RECORD_VALUE_OFFSET + valueLength;
    }

    public static int recordValueOffset(int entryOffset)
    {
        return entryOffset + RECORD_VALUE_OFFSET;
    }

    public static int recordKeyOffset(int entryOffset)
    {
        return entryOffset + RECORD_KEY_OFFSET;
    }

    public static int requiredIndexBufferSize(int indexSize)
    {
        return (indexSize * SIZE_OF_INT) + INDEX_OFFSET;
    }

    public static int requiredBlockBufferSize(int indexSize, int blockSize)
    {
        return indexSize * (BLOCK_DATA_OFFSET + blockSize);
    }
}
