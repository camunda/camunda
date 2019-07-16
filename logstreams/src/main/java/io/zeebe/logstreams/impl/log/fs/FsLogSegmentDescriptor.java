/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
