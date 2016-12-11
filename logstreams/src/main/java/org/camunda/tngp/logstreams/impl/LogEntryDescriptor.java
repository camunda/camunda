package org.camunda.tngp.logstreams.impl;

import static org.agrona.BitUtil.*;

import org.agrona.BitUtil;

/**
 *  * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |            VERSION             |              R               |
 *  +---------------------------------------------------------------+
 *  |                            POSITION                           |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |                           PRODUCER ID                         |
 *  +---------------------------------------------------------------+
 *  |                      SOURCE EVENT STREAM ID                   |
 *  +---------------------------------------------------------------+
 *  |                      SOURCE EVENT POSITION                    |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |            KEY TYPE            |         KEY LENGTH           |
 *  +---------------------------------------------------------------+
 *  |                            ...KEY...                          |
 *  +---------------------------------------------------------------+
 *  |         METADATA LENGTH        |       ...METADATA...         |
 *  +---------------------------------------------------------------+
 *  |                           ...VALUE...                         |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 *
 */
public class LogEntryDescriptor
{
    public static final short KEY_TYPE_UINT64 = 1;
    public static final short KEY_TYPE_BYTES = 2;

    public static final int METADATA_HEADER_LENGTH = BitUtil.SIZE_OF_SHORT;

    public static final int VERSION_OFFSET;

    public static final int POSITION_OFFSET;

    public static final int SOURCE_EVENT_POSITION_OFFSET;

    public static final int SOURCE_EVENT_LOG_STREAM_ID_OFFSET;

    public static final int PRODUCER_ID_OFFSET;

    public static final int KEY_TYPE_OFFSET;

    public static final int KEY_LENGTH_OFFSET;

    public static final int KEY_OFFSET;

    public static final int HEADER_BLOCK_LENGHT;

    static
    {
        int offset = 0;

        VERSION_OFFSET = offset;
        offset += SIZE_OF_SHORT;

        // reserved offset
        offset += SIZE_OF_SHORT;

        POSITION_OFFSET = offset;
        offset += SIZE_OF_LONG;

        PRODUCER_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        SOURCE_EVENT_LOG_STREAM_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        SOURCE_EVENT_POSITION_OFFSET = offset;
        offset += SIZE_OF_LONG;

        KEY_TYPE_OFFSET = offset;
        offset += SIZE_OF_SHORT;

        KEY_LENGTH_OFFSET = offset;
        offset += SIZE_OF_SHORT;

        HEADER_BLOCK_LENGHT = offset;

        KEY_OFFSET = offset;
    }

    public static int headerLength(int keyLength, int metadataLength)
    {
        return HEADER_BLOCK_LENGHT + keyLength + METADATA_HEADER_LENGTH + metadataLength;
    }

    public static int positionOffset(int offset)
    {
        return POSITION_OFFSET + offset;
    }

    public static int keyTypeOffset(int offset)
    {
        return KEY_TYPE_OFFSET + offset;
    }

    public static int keyLengthOffset(int offset)
    {
        return KEY_LENGTH_OFFSET + offset;
    }

    public static int keyOffset(int offset)
    {
        return KEY_OFFSET + offset;
    }

    public static int sourceEventPositionOffset(int offset)
    {
        return SOURCE_EVENT_POSITION_OFFSET + offset;
    }

    public static int sourceEventLogStreamIdOffset(int offset)
    {
        return SOURCE_EVENT_LOG_STREAM_ID_OFFSET + offset;
    }

    public static int producerIdOffset(int offset)
    {
        return PRODUCER_ID_OFFSET + offset;
    }

    public static int metadataLengthOffset(int offset, int keyLength)
    {
        return KEY_OFFSET + keyLength + offset;
    }

    public static int metadataOffset(int offset, int keyLength)
    {
        return KEY_OFFSET + keyLength + METADATA_HEADER_LENGTH + offset;
    }

    public static int valueOffset(int offset, int keyLength, int metadataLength)
    {
        return KEY_OFFSET + keyLength + METADATA_HEADER_LENGTH + metadataLength + offset;
    }

}
