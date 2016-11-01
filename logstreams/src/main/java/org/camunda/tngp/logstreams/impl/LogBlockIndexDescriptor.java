package org.camunda.tngp.logstreams.impl;

import static org.agrona.BitUtil.*;

public class LogBlockIndexDescriptor
{
    public static final int ENTRY_VIRTUAL_POSITION_OFFSET;

    public static final int ENTRY_PHYSICAL_POSITION_OFFSET;

    public static final int ENTRY_LENGTH;

    public static final int DATA_OFFSET;

    public static final int METADATA_OFFSET;

    public static final int INDEX_SIZE_OFFSET;

    static
    {
        int offset = 0;

        ENTRY_VIRTUAL_POSITION_OFFSET = offset;
        offset += SIZE_OF_LONG;

        ENTRY_PHYSICAL_POSITION_OFFSET = offset;
        offset += SIZE_OF_LONG;

        ENTRY_LENGTH = offset;

        offset = 2 * CACHE_LINE_LENGTH;

        METADATA_OFFSET = offset;

        offset += 2 * CACHE_LINE_LENGTH;
        INDEX_SIZE_OFFSET = offset;
        offset += 2 * CACHE_LINE_LENGTH;

        DATA_OFFSET = offset;
    }

    public static int entryLength()
    {
        return ENTRY_LENGTH;
    }

    public static int entryLogPositionOffset(int offset)
    {
        return offset + ENTRY_VIRTUAL_POSITION_OFFSET;
    }

    public static int entryAddressOffset(int offset)
    {
        return offset + ENTRY_PHYSICAL_POSITION_OFFSET;
    }

    public static int indexSizeOffset()
    {
        return INDEX_SIZE_OFFSET;
    }

    public static int entryOffset(int entryIdx)
    {
        return dataOffset() + (entryIdx * entryLength());
    }

    public static int dataOffset()
    {
        return DATA_OFFSET;
    }
}
