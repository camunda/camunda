/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor.alignedLength;
import static io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor.lengthOffset;
import static io.camunda.zeebe.logstreams.impl.serializer.DataFrameDescriptor.messageOffset;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import io.camunda.zeebe.protocol.Protocol;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * *
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |            VERSION             |    FLAGS      |       R      |
 *  +---------------------------------------------------------------+
 *  |                            POSITION                           |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |                      SOURCE EVENT POSITION                    |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |                               KEY                             |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |                           TIMESTAMP                           |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |                        METADATA LENGTH                        |
 *  +---------------------------------------------------------------+
 *  |                         ...METADATA...                        |
 *  +---------------------------------------------------------------+
 *  |                           ...VALUE...                         |
 *  +---------------------------------------------------------------+
 * </pre>
 */
public final class LogEntryDescriptor {

  public static final long KEY_NULL_VALUE = -1;

  // When VERSION is incremented, also update the version check in getMetadataLength
  private static final short VERSION = 1;
  private static final int VERSION_OFFSET;

  // Contains arbitrary flags, currently only the `skipProcessing` flag.
  private static final int FLAGS_OFFSET;
  private static final int POSITION_OFFSET;

  private static final int SOURCE_EVENT_POSITION_OFFSET;

  private static final int KEY_OFFSET;

  private static final int TIMESTAMP_OFFSET;

  private static final int METADATA_LENGTH_OFFSET;

  private static final int HEADER_BLOCK_LENGTH;

  private static final int METADATA_OFFSET;

  static {
    int offset = 0;

    VERSION_OFFSET = offset;
    offset += SIZE_OF_SHORT;

    FLAGS_OFFSET = offset;
    offset += Byte.BYTES;

    // reserved
    offset += Byte.BYTES;

    POSITION_OFFSET = offset;
    offset += SIZE_OF_LONG;

    SOURCE_EVENT_POSITION_OFFSET = offset;
    offset += SIZE_OF_LONG;

    KEY_OFFSET = offset;
    offset += SIZE_OF_LONG;

    TIMESTAMP_OFFSET = offset;
    offset += SIZE_OF_LONG;

    METADATA_LENGTH_OFFSET = offset;
    offset += SIZE_OF_INT;

    HEADER_BLOCK_LENGTH = offset;

    METADATA_OFFSET = offset;
  }

  public static int versionOffset(final int offset) {
    return VERSION_OFFSET + offset;
  }

  public static void setVersion(final MutableDirectBuffer buffer, final int offset) {
    buffer.putShort(versionOffset(offset), VERSION, Protocol.ENDIANNESS);
  }

  public static short getVersion(final DirectBuffer buffer, final int offset) {
    return buffer.getShort(versionOffset(offset), Protocol.ENDIANNESS);
  }

  public static int getFragmentLength(final DirectBuffer buffer, final int offset) {
    return alignedLength(buffer.getInt(lengthOffset(offset), Protocol.ENDIANNESS));
  }

  public static int headerLength(final int metadataLength) {
    return HEADER_BLOCK_LENGTH + metadataLength;
  }

  public static int positionOffset(final int offset) {
    return POSITION_OFFSET + offset;
  }

  public static long getPosition(final DirectBuffer buffer, final int offset) {
    return buffer.getLong(positionOffset(messageOffset(offset)), Protocol.ENDIANNESS);
  }

  public static void setPosition(
      final MutableDirectBuffer buffer, final int offset, final long position) {
    buffer.putLong(positionOffset(offset), position, Protocol.ENDIANNESS);
  }

  public static int sourceEventPositionOffset(final int offset) {
    return SOURCE_EVENT_POSITION_OFFSET + offset;
  }

  public static long getSourceEventPosition(final DirectBuffer buffer, final int offset) {
    return buffer.getLong(sourceEventPositionOffset(offset), Protocol.ENDIANNESS);
  }

  public static void setSourceEventPosition(
      final MutableDirectBuffer buffer, final int offset, final long sourceEventPosition) {
    buffer.putLong(sourceEventPositionOffset(offset), sourceEventPosition, Protocol.ENDIANNESS);
  }

  public static int keyOffset(final int offset) {
    return KEY_OFFSET + offset;
  }

  public static long getKey(final DirectBuffer buffer, final int offset) {
    return buffer.getLong(keyOffset(offset), Protocol.ENDIANNESS);
  }

  public static void setKey(final MutableDirectBuffer buffer, final int offset, final long key) {
    buffer.putLong(keyOffset(offset), key, Protocol.ENDIANNESS);
  }

  public static int flagsOffset(final int offset) {
    return FLAGS_OFFSET + offset;
  }

  public static boolean shouldSkipProcessing(final DirectBuffer buffer, final int offset) {
    return buffer.getByte(flagsOffset(offset)) != 0;
  }

  public static void skipProcessing(final MutableDirectBuffer buffer, final int offset) {
    buffer.putByte(flagsOffset(offset), (byte) 1);
  }

  public static int timestampOffset(final int offset) {
    return TIMESTAMP_OFFSET + offset;
  }

  public static long getTimestamp(final DirectBuffer buffer, final int offset) {
    return buffer.getLong(timestampOffset(offset), Protocol.ENDIANNESS);
  }

  public static void setTimestamp(
      final MutableDirectBuffer buffer, final int offset, final long timestamp) {
    buffer.putLong(timestampOffset(offset), timestamp, Protocol.ENDIANNESS);
  }

  public static int metadataLengthOffset(final int offset) {
    return METADATA_LENGTH_OFFSET + offset;
  }

  public static int getMetadataLength(final DirectBuffer buffer, final int offset) {
    if (getVersion(buffer, offset) == 1) {
      return buffer.getInt(metadataLengthOffset(offset), Protocol.ENDIANNESS);
    } else {
      // previous versions never set a version, so the version could be a garbage value or 0.
      return buffer.getShort(metadataLengthOffset(offset), Protocol.ENDIANNESS);
    }
  }

  public static void setMetadataLength(
      final MutableDirectBuffer buffer, final int offset, final int metadataLength) {
    buffer.putInt(metadataLengthOffset(offset), metadataLength, Protocol.ENDIANNESS);
  }

  public static int metadataOffset(final int offset) {
    return METADATA_OFFSET + offset;
  }

  public static int valueOffset(final int offset, final int metadataLength) {
    return METADATA_OFFSET + metadataLength + offset;
  }
}
