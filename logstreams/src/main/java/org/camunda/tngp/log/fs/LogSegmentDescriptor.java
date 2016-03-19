package org.camunda.tngp.log.fs;

import static uk.co.real_logic.agrona.BitUtil.*;

/**
 * Segment layout
 * <pre>
 *  +----------------------------+
 *  |         Metadata           |
 *  +----------------------------+
 *  |           Data             |
 *  +----------------------------+
 * </pre>
 *
 * Metadata layout
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                          Segment Id                           |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |            Version           |          [unused]              |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                         Segment Size                          |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                       Cache Line Padding                    ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 *  |                         Segment Tail                          |
 *  +---------------------------------------------------------------+
 *  |                       Cache Line Padding                    ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 *
 * </pre>
 */
public class LogSegmentDescriptor
{

    public final static int SEGMENT_ID_OFFSET;
    public final static int VERSION_OFFSET;
    public final static int SEGMENT_SIZE_OFFSET;
    public final static int SEGMENT_TAIL_OFFSET;

    public final static int METADATA_LENGTH;

    static
    {
        int offset = 0;

        SEGMENT_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        VERSION_OFFSET = offset;
        offset += SIZE_OF_INT;

        SEGMENT_SIZE_OFFSET = offset;
        offset += SIZE_OF_INT;

        offset += (2* CACHE_LINE_LENGTH);

        SEGMENT_TAIL_OFFSET = offset;
        offset += (2* CACHE_LINE_LENGTH);

        METADATA_LENGTH = align(offset, 8);
    }
}
