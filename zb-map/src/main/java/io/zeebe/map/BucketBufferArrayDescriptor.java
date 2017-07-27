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
import static org.agrona.BitUtil.SIZE_OF_LONG;

/**
 **
 * BucketBufferArray layout
 *
 * The main BucketBufferArray header contains the following information's: *
 *
 * <pre>
 *  0               1               2               3
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                         BUCKET BUFFER COUNT                 |
 *  +-------------------------------------------------------------+
 *  |                         BUCKET COUNT                        |
 *  +-------------------------------------------------------------+
 *  |                         BLOCK COUNT                         |
 *  |                                                             |
 *  +-------------------------------------------------------------+
 * </pre>
 *
 * The BUCKET BUFFER COUNT contains the count of all existing bucket buffers.
 * The BUCKET COUNT contains the count of all existing buckets.
 * The BLOCK COUNT contains the count of all existing block.
 * The last two headers are mainly used to calculate fast the load factor and also be available after
 * deserialization.
 *
 * There can exist multiple BucketBuffers, each of them have the same layout.
 * These BucketBuffers can contain till 32 buckets. The current bucket count is
 * stored in the BUCKET COUNT header.
 *
 * <pre>
 *  +----------------------------+
 *  |         BUCKET COUNT       |
 *  +----------------------------+
 *  |           BUCKET 0         |
 *  +----------------------------+
 *  |             ...            |
 *  +----------------------------+
 *  |           BUCKET 32        |
 *  +----------------------------+
 * </pre>
 *
 * Each bucket has the following layout
 *
 * <pre>
 *  0               1               2               3
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                       BUCKET FILL COUNT                     |
 *  +-------------------------------------------------------------+
 *  |                       BUCKET LENGTH                         |
 *  +-------------------------------------------------------------+
 *  |                       BUCKET ID                             |
 *  +-------------------------------------------------------------+
 *  |                       BUCKET DEPTH                          |
 *  +-------------------------------------------------------------+
 *  |                       BUCKET OVERFLOW                       |
 *  |                       POINTER                               |
 *  +-------------------------------------------------------------+
 *  |                                                             |
 *  |                       BLOCK DATA                           ...
 * ...                                                            |
 *  +-------------------------------------------------------------+
 * </pre>
 *
 * The block data contains the blocks
 *
 * Each block has the following layout
 *
 * <pre>
 *  0               1               2               3
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                             Key                            ...
 * ...                                                            |
 *  +-------------------------------------------------------------+
 *  |                             Value                          ...
 * ...                                                            |
 *  +-------------------------------------------------------------+
 * </pre>
 *
 */
public class BucketBufferArrayDescriptor
{

    public static final int MAIN_BUFFER_COUNT_OFFSET;
    public static final int MAIN_BUCKET_COUNT_OFFSET;
    public static final int MAIN_BLOCK_COUNT_OFFSET;
    public static final int MAIN_BUCKET_BUFFER_HEADER_LEN;

    public static final int BUCKET_BUFFER_BUCKET_COUNT_OFFSET;
    public static final int BUCKET_BUFFER_HEADER_LENGTH;

    public static final int BUCKET_FILL_COUNT_OFFSET;
    public static final int BUCKET_LENGTH_OFFSET;
    public static final int BUCKET_ID_OFFSET;
    public static final int BUCKET_DEPTH_OFFSET;
    public static final int BUCKET_OVERFLOW_POINTER_OFFSET;

    public static final int BUCKET_HEADER_LENGTH;
    public static final int BUCKET_DATA_OFFSET;

    public static final int BLOCK_KEY_OFFSET;

    static
    {
        int offset = 0;
        // MAIN BUCKET BUFFER ARRAY HEADER ////////////////
        MAIN_BUFFER_COUNT_OFFSET = offset;
        offset += SIZE_OF_INT;

        MAIN_BUCKET_COUNT_OFFSET = offset;
        offset += SIZE_OF_INT;

        MAIN_BLOCK_COUNT_OFFSET = offset;
        offset += SIZE_OF_LONG;

        MAIN_BUCKET_BUFFER_HEADER_LEN = offset;

        // BUCKET BUFFER HEADER ////////////////
        offset = 0;
        BUCKET_BUFFER_BUCKET_COUNT_OFFSET = 0;
        offset += SIZE_OF_INT;

        BUCKET_BUFFER_HEADER_LENGTH = offset;

        // BUCKET HEADER ////////////////
        offset = 0;

        BUCKET_FILL_COUNT_OFFSET = offset;
        offset += SIZE_OF_INT;

        BUCKET_LENGTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        BUCKET_ID_OFFSET = offset;
        offset += SIZE_OF_INT;

        BUCKET_DEPTH_OFFSET = offset;
        offset += SIZE_OF_INT;

        BUCKET_OVERFLOW_POINTER_OFFSET = offset;
        offset += SIZE_OF_LONG;

        BUCKET_DATA_OFFSET = offset;

        BUCKET_HEADER_LENGTH = offset;

        // BLOCK HEADER ////////////////
        offset = 0;
        BLOCK_KEY_OFFSET = offset;
    }

    public static int getBlockLength(final int keyLength, final int valueLength)
    {
        return addExact(keyLength, valueLength);
    }

    public static long getBlockValueOffset(final long offset, final int keyLength)
    {
        return offset + BLOCK_KEY_OFFSET + keyLength;
    }

}
