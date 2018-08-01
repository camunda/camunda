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
package io.zeebe.logstreams.impl.log.fs;

import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.align;
import static org.agrona.IoUtil.BLOCK_SIZE;

/**
 * Segment layout
 *
 * <pre>
 *  +----------------------------+
 *  |         Metadata           |
 *  +----------------------------+
 *  |           Data             |
 *  +----------------------------+
 * </pre>
 *
 * Metadata layout
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                          Segment Id                           |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |            Version           |          [unused]              |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                        Segment Capacity                       |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                       Cache Line Padding                    ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 *  |                         Segment Size                          |
 *  +---------------------------------------------------------------+
 *  |                       Cache Line Padding                    ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 *
 * </pre>
 */
public class FsLogSegmentDescriptor {

  public static final int SEGMENT_ID_OFFSET;
  public static final int VERSION_OFFSET;
  public static final int SEGMENT_CAPACITY_OFFSET;
  public static final int SEGMENT_SIZE_OFFSET;

  public static final int METADATA_LENGTH;

  static {
    int offset = 0;

    SEGMENT_ID_OFFSET = offset;
    offset += SIZE_OF_INT;

    VERSION_OFFSET = offset;
    offset += SIZE_OF_INT;

    SEGMENT_CAPACITY_OFFSET = offset;
    offset += (2 * CACHE_LINE_LENGTH);

    SEGMENT_SIZE_OFFSET = offset;
    offset += (2 * CACHE_LINE_LENGTH);

    METADATA_LENGTH = align(offset, BLOCK_SIZE);
  }
}
