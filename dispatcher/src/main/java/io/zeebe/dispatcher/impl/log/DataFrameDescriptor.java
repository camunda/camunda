/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.impl.log;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_SHORT;
import static org.agrona.BitUtil.align;

/**
 *
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |R|                        Frame Length                         |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-------------------------------+
 *  |  Version      |B|E|F| Flags   |             Type              |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-------------------------------+
 *  |                            StreamId                           |
 *  +---------------------------------------------------------------+
 *  |                                                               |
 *  |                            Message                           ...
 * ...                                                              |
 *  +---------------------------------------------------------------+
 * </pre>
 *
 * <p>Frame length: Including the length of the header to ensure length is always > 0 which is
 * important for distinguishing committed from uncommitted but claimed fragments.
 *
 * <p>Flags:
 *
 * <ul>
 *   <li>B: Begin Batch
 *   <li>E: End Batch
 *   <li>F: Failed (e.g. set by a prior subscriber)
 */
public class DataFrameDescriptor {

  public static final int FRAME_ALIGNMENT = 8;

  public static final int FRAME_LENGTH_OFFSET;

  public static final int VERSION_OFFSET;

  public static final int FLAGS_OFFSET;

  public static final int TYPE_OFFSET;

  public static final int STREAM_ID_OFFSET;

  public static final short TYPE_MESSAGE = 0;

  public static final short TYPE_PADDING = 1;

  public static final int HEADER_LENGTH;
  public static final int FLAG_BATCH_BEGIN_BITMASK = 0b1000_0000;
  public static final int FLAG_BATCH_END_BITMASK = 0b0100_0000;
  public static final int FLAG_FAILED_BITMASK = 0b0010_0000;

  static {
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

  public static int lengthOffset(int offset) {
    return offset + FRAME_LENGTH_OFFSET;
  }

  public static int versionOffset(int offset) {
    return offset + VERSION_OFFSET;
  }

  public static int flagsOffset(int offset) {
    return offset + FLAGS_OFFSET;
  }

  public static int typeOffset(int offset) {
    return offset + TYPE_OFFSET;
  }

  public static int streamIdOffset(int offset) {
    return offset + STREAM_ID_OFFSET;
  }

  public static int messageOffset(int offset) {
    return offset + HEADER_LENGTH;
  }

  public static int alignedFramedLength(int msgLength) {
    return alignedLength(framedLength(msgLength));
  }

  public static int alignedLength(int msgLength) {
    return align(msgLength, FRAME_ALIGNMENT);
  }

  public static int framedLength(int msgLength) {
    return msgLength + HEADER_LENGTH;
  }

  public static int messageLength(int framedLength) {
    return framedLength - HEADER_LENGTH;
  }

  public static boolean flagFailed(byte flags) {
    return (flags & FLAG_FAILED_BITMASK) != 0;
  }

  public static byte enableFlagFailed(byte flags) {
    return (byte) (flags | FLAG_FAILED_BITMASK);
  }

  public static boolean flagBatchBegin(byte flags) {
    return (flags & FLAG_BATCH_BEGIN_BITMASK) != 0;
  }

  public static byte enableFlagBatchBegin(byte flags) {
    return (byte) (flags | FLAG_BATCH_BEGIN_BITMASK);
  }

  public static boolean flagBatchEnd(byte flags) {
    return (flags & FLAG_BATCH_END_BITMASK) != 0;
  }

  public static byte enableFlagBatchEnd(byte flags) {
    return (byte) (flags | FLAG_BATCH_END_BITMASK);
  }
}
