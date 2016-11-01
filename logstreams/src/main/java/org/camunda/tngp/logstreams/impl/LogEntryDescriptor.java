package org.camunda.tngp.logstreams.impl;

import static org.agrona.BitUtil.*;

public class LogEntryDescriptor
{
    public static final short KEY_TYPE_UINT64 = 1;
    public static final short KEY_TYPE_BYTES = 2;

    public static final int POSITION_OFFSET;

    public static final int KEY_TYPE_OFFSET;

    public static final int KEY_LENGTH_OFFSET;

    public static final int KEY_OFFSET;

    public static final int HEADER_BLOCK_LENGHT;

    static
    {
        int offset = 0;

        POSITION_OFFSET = offset;
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

}
