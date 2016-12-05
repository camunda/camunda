package org.camunda.tngp.logstreams.impl;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

public class LogEntryDescriptor
{
    public static final short KEY_TYPE_UINT64 = 1;
    public static final short KEY_TYPE_BYTES = 2;

    public static final int POSITION_OFFSET;

    public static final int SOURCE_EVENT_POSITION_OFFSET;

    public static final int SOURCE_EVENT_LOG_STREAM_ID_OFFSET;

    public static final int STREAM_PROCESSOR_ID_OFFSET;

    public static final int KEY_TYPE_OFFSET;

    public static final int KEY_LENGTH_OFFSET;

    public static final int KEY_OFFSET;

    public static final int HEADER_BLOCK_LENGHT;

    static
    {
        int offset = 0;

        POSITION_OFFSET = offset;
        offset += SIZE_OF_LONG;

        SOURCE_EVENT_LOG_STREAM_ID_OFFSET = offset;
        offset += SIZE_OF_LONG;

        SOURCE_EVENT_POSITION_OFFSET = offset;
        offset += SIZE_OF_LONG;

        STREAM_PROCESSOR_ID_OFFSET = offset;
        offset += SIZE_OF_LONG;

        KEY_TYPE_OFFSET = offset;
        offset += SIZE_OF_SHORT;

        KEY_LENGTH_OFFSET = offset;
        offset += SIZE_OF_SHORT;

        HEADER_BLOCK_LENGHT = offset;

        KEY_OFFSET = offset;
    }

    public static int headerLength(int keyLength)
    {
        return HEADER_BLOCK_LENGHT + keyLength;
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

    public static int valueOffset(int offset, int keyLength)
    {
        return HEADER_BLOCK_LENGHT + keyLength + offset;
    }

    public static int sourceEventPositionOffset(int offset)
    {
        return SOURCE_EVENT_POSITION_OFFSET + offset;
    }

    public static int sourceEventLogStreamIdOffset(int offset)
    {
        return SOURCE_EVENT_LOG_STREAM_ID_OFFSET + offset;
    }

    public static int streamProcessorIdOffset(int offset)
    {
        return STREAM_PROCESSOR_ID_OFFSET + offset;
    }

}
