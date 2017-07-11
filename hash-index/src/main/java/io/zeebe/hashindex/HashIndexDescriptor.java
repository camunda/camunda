/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.hashindex;

import static org.agrona.BitUtil.SIZE_OF_INT;


/**
 * The index has 2 Buffers: the "index buffer" and the "block buffer".
 *
 * Index Buffer layout
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                                                               |
 *  |                           INDEX DATA                         ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 * Explanation:
 *
 * <ul>
 * <li>Block Length: the max length of a data block in bytes (immutable)</li>
 * <li>Record Key Length: length of a record key in bytes (immutable)</li>
 * <li>Size: the size of the index in bytes. Must be a power of 2.
 * Controls how many index entries there are</li>
 * </ul>
 *
 * Block Buffer layout
 *
 * <pre>
 *  +----------------------------+
 *  |          BLOCK COUT        |
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
 *  |                             LENGTH                            |
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
 *  |                          Value Length                         |
 *  +---------------------------------------------------------------+
 *  |                             Key                              ...
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
    public static final int INDEX_SIZE_OFFSET;
    public static final int INDEX_OFFSET;

    public static final int BLOCK_COUNT_OFFSET;
    public static final int BLOCK_BUFFER_HEADER_LENGTH;
    public static final int BLOCK_FILL_COUNT_OFFSET;
    public static final int BLOCK_LENGTH_OFFSET;
    public static final int BLOCK_ID_OFFSET;
    public static final int BLOCK_DEPTH_OFFSET;
    public static final int BLOCK_DATA_OFFSET;

    public static final int RECORD_VALUE_LENGTH_OFFSET;
    public static final int RECORD_KEY_OFFSET;

    public static final byte TYPE_RECORD = 1;

    static
    {
        int offset = 0;

        INDEX_SIZE_OFFSET = offset;
        offset += SIZE_OF_INT;

        INDEX_OFFSET = offset;

        offset = 0;
        BLOCK_COUNT_OFFSET = 0;
        offset += SIZE_OF_INT;

        BLOCK_BUFFER_HEADER_LENGTH = offset;

        offset = 0;

        BLOCK_FILL_COUNT_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_DEPTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_DATA_OFFSET = offset;

        offset = 0;

        RECORD_VALUE_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        RECORD_KEY_OFFSET = offset;
    }
}
