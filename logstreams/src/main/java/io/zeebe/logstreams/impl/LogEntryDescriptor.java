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
package io.zeebe.logstreams.impl;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import io.zeebe.protocol.Protocol;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * *
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |            VERSION             |              R               |
 *  +---------------------------------------------------------------+
 *  |                            POSITION                           |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |                           RAFT TERM ID                        |
 *  +---------------------------------------------------------------+
 *  |                           PRODUCER ID                         |
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
 *  |        METADATA LENGTH         |       unused                 |
 *  +---------------------------------------------------------------+
 *  |                         ...METADATA...                        |
 *  +---------------------------------------------------------------+
 *  |                           ...VALUE...                         |
 *  +---------------------------------------------------------------+
 * </pre>
 */
public class LogEntryDescriptor {

  public static final long KEY_NULL_VALUE = -1;

  public static final int VERSION_OFFSET;

  public static final int POSITION_OFFSET;

  public static final int PRODUCER_ID_OFFSET;

  public static final int SOURCE_EVENT_POSITION_OFFSET;

  public static final int KEY_OFFSET;

  public static final int TIMESTAMP_OFFSET;

  public static final int METADATA_LENGTH_OFFSET;

  public static final int HEADER_BLOCK_LENGTH;

  public static final int METADATA_OFFSET;

  static {
    int offset = 0;

    VERSION_OFFSET = offset;
    offset += SIZE_OF_SHORT;

    // reserved offset
    offset += SIZE_OF_SHORT;

    POSITION_OFFSET = offset;
    offset += SIZE_OF_LONG;

    PRODUCER_ID_OFFSET = offset;
    offset += SIZE_OF_INT;

    SOURCE_EVENT_POSITION_OFFSET = offset;
    offset += SIZE_OF_LONG;

    KEY_OFFSET = offset;
    offset += SIZE_OF_LONG;

    TIMESTAMP_OFFSET = offset;
    offset += SIZE_OF_LONG;

    METADATA_LENGTH_OFFSET = offset;
    offset += SIZE_OF_SHORT;

    // UNUSED BLOCK
    offset += SIZE_OF_SHORT;

    HEADER_BLOCK_LENGTH = offset;

    METADATA_OFFSET = offset;
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

  public static int producerIdOffset(final int offset) {
    return PRODUCER_ID_OFFSET + offset;
  }

  public static int getProducerId(final DirectBuffer buffer, final int offset) {
    return buffer.getInt(producerIdOffset(offset), Protocol.ENDIANNESS);
  }

  public static void setProducerId(
      final MutableDirectBuffer buffer, final int offset, final int producerId) {
    buffer.putInt(producerIdOffset(offset), producerId, Protocol.ENDIANNESS);
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

  public static short getMetadataLength(final DirectBuffer buffer, final int offset) {
    return buffer.getShort(metadataLengthOffset(offset), Protocol.ENDIANNESS);
  }

  public static void setMetadataLength(
      final MutableDirectBuffer buffer, final int offset, final short metadataLength) {
    buffer.putShort(metadataLengthOffset(offset), metadataLength, Protocol.ENDIANNESS);
  }

  public static int metadataOffset(final int offset) {
    return METADATA_OFFSET + offset;
  }

  public static int valueOffset(final int offset, final int metadataLength) {
    return METADATA_OFFSET + metadataLength + offset;
  }
}
