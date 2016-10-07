package org.camunda.tngp.broker.clustering.util;

import static org.agrona.BitUtil.*;

/**
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                              Port                             |
 *  +---------------------------------------------------------------+
 *  |                           Host Length                         |
 *  +---------------------------------------------------------------+
 *  |                                                               |
 *  |                            Host                              ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 */
public class EndpointDescriptor
{
    public static final int PORT_OFFSET;

    public static final int HOST_LENGTH_OFFSET;

    public static final int HEADER_OFFSET;

    static
    {
        int offset = 0;

        PORT_OFFSET = offset;
        offset += SIZE_OF_INT;

        HOST_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        HEADER_OFFSET = offset;
    }

    public static final int portOffset(final int offset)
    {
        return PORT_OFFSET + offset;
    }

    public static int hostLengthOffset(final int offset)
    {
        return HOST_LENGTH_OFFSET + offset;
    }

    public static int hostOffset(final int offset)
    {
        return HEADER_OFFSET + offset;
    }

    public static int requiredBufferCapacity(final int maxHostLength)
    {
        return HEADER_OFFSET + maxHostLength;
    }
}
