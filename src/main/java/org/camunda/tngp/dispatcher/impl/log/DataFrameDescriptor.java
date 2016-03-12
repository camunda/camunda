package org.camunda.tngp.dispatcher.impl.log;

import static uk.co.real_logic.agrona.BitUtil.*;

/**
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |R|                        Frame Length                         |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-------------------------------+
 *  |  Version      |B|E| Flags     |             Type              |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-------------------------------+
 *  |                            StreamId                           |
 *  +---------------------------------------------------------------+
 *  |                                                               |
 *  |                            Message                           ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 */
public class DataFrameDescriptor
{

    public static final int FRAME_ALIGNMENT = 8;

    public static final int FRAME_LENGTH_OFFSET;

    public static final int VERSION_OFFSET;

    public static final int FLAGS_OFFSET;

    public static final int TYPE_OFFSET;

    public static final int STREAM_ID_OFFSET;

    public static final short TYPE_MESSAGE = 0;

    public static final short TYPE_PADDING = 1;

    public static final int HEADER_LENGTH;

    static
    {
        // init offsets

        int offset = 0;

        FRAME_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        VERSION_OFFSET = offset;
        offset += 1;

        FLAGS_OFFSET = offset;
        offset += 1;

        TYPE_OFFSET = offset + SIZE_OF_SHORT - 2;
        offset += SIZE_OF_SHORT;

        STREAM_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        HEADER_LENGTH = offset;
    }

    public static int lengthOffset(int offset)
    {
        return offset + FRAME_LENGTH_OFFSET;
    }

    public static int versionOffset(int offset)
    {
        return offset + VERSION_OFFSET;
    }

    public static int flagsOffset(int offset)
    {
        return offset + FLAGS_OFFSET;
    }

    public static int typeOffset(int offset)
    {
        return offset + TYPE_OFFSET;
    }

    public static int streamIdOffset(int offset)
    {
        return offset + STREAM_ID_OFFSET;
    }

    public static int messageOffset(int offset)
    {
        return offset + HEADER_LENGTH;
    }

    public static int alignedLength(int msgLength)
    {
        return align(msgLength + HEADER_LENGTH, FRAME_ALIGNMENT);
    }

}
