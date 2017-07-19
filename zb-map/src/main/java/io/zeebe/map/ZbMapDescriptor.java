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
package io.zeebe.map;

import static java.lang.Math.addExact;
import static org.agrona.BitUtil.SIZE_OF_INT;


/**
 * The map has 2 Buffers: the "hash table buffer" and the "buckets buffer".
 *
 * Hash table buffer layout
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                                                               |
 *  |                           MAP DATA                         ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 * Explanation:
 *
 * <ul>
 * <li>Block Length: the max length of a data block in bytes (immutable)</li>
 * <li>Record Key Length: length of a record key in bytes (immutable)</li>
 * <li>Size: the size of the map in bytes. Must be a power of 2.
 * Controls how many entries there are</li>
 * </ul>
 *
 * BUCKET Buffer layout
 *
 * <pre>
 *  +----------------------------+
 *  |         BUCKET COUNT       |
 *  +----------------------------+
 *  |           BUCKET 0         |
 *  +----------------------------+
 *  |             ...            |
 *  +----------------------------+
 *  |           BUCKET n         |
 *  +----------------------------+
 * </pre>
 *
 * Each bucket has the following layout
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                         BUCKET FILL COUNT                     |
 *  +---------------------------------------------------------------+
 *  |                         BUCKET LENGTH                         |
 *  +---------------------------------------------------------------+
 *  |                         BUCKET ID                             |
 *  +---------------------------------------------------------------+
 *  |                         BUCKET DEPTH                          |
 *  +---------------------------------------------------------------+
 *  |                                                               |
 *  |                         BLOCK DATA                          ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 * The block data contains the blocks
 *
 * Each block has the following layout
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
public class ZbMapDescriptor
{
    public static final int HASH_TABLE_SIZE_OFFSET;
    public static final int HASH_TABLE_OFFSET;

    public static final int BUCKET_COUNT_OFFSET;
    public static final int BUCKET_BUFFER_HEADER_LENGTH;
    public static final int BUCKET_FILL_COUNT_OFFSET;
    public static final int BUCKET_LENGTH_OFFSET;
    public static final int BUCKET_ID_OFFSET;
    public static final int BUCKET_DEPTH_OFFSET;
    public static final int BUCKET_HEADER_LENGTH;
    public static final int BUCKET_DATA_OFFSET;

    public static final int BLOCK_VALUE_LENGTH_OFFSET;
    public static final int BLOCK_KEY_OFFSET;
    public static final int BLOCK_HEADER_LENGTH;

    static
    {
        int offset = 0;

        HASH_TABLE_SIZE_OFFSET = offset;
        offset += SIZE_OF_INT;

        HASH_TABLE_OFFSET = offset;

        offset = 0;
        BUCKET_COUNT_OFFSET = 0;
        offset += SIZE_OF_INT;

        BUCKET_BUFFER_HEADER_LENGTH = offset;

        offset = 0;

        BUCKET_FILL_COUNT_OFFSET = offset;
        offset += SIZE_OF_INT;

        BUCKET_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        BUCKET_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        BUCKET_DEPTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        BUCKET_DATA_OFFSET = offset;

        BUCKET_HEADER_LENGTH = offset;


        offset = 0;

        BLOCK_VALUE_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        BLOCK_KEY_OFFSET = offset;

        BLOCK_HEADER_LENGTH = BLOCK_KEY_OFFSET;
    }

    public static int getBlockLength(final int keyLength, final int valueLength)
    {
        return addExact(BLOCK_HEADER_LENGTH, addExact(keyLength, valueLength));
    }

    public static long getBlockValueOffset(final long offset, final int keyLength)
    {
        return offset + BLOCK_KEY_OFFSET + keyLength;
    }

}
