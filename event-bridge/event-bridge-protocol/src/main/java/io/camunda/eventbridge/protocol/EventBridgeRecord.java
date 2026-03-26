/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Describes the binary format of an EventBridge event record stored inside each entry's value.
 *
 * <p>Layout:
 *
 * <pre>
 * ┌─────────┬─────────────┬───────────┬──────────┬───────────────┬──────────────┐
 * │ version │ contentType │ keyLength │ key      │ payloadLength │ payload      │
 * │ (1b)    │ (1b)        │ (2b)      │ (var)    │ (4b)          │ (var)        │
 * └─────────┴─────────────┴───────────┴──────────┴───────────────┴──────────────┘
 * </pre>
 *
 * <p>All access is via static methods on raw buffers — no object allocation for reads.
 */
public final class EventBridgeRecord {

  public static final int CURRENT_VERSION = 1;

  public static final int VERSION_OFFSET = 0;
  public static final int VERSION_LENGTH = Byte.BYTES;

  public static final int CONTENT_TYPE_LENGTH = Byte.BYTES;
  public static final int KEY_LENGTH_SIZE = Short.BYTES;
  public static final int MIN_RECORD_LENGTH =
      VERSION_LENGTH + CONTENT_TYPE_LENGTH + KEY_LENGTH_SIZE + Integer.BYTES;
  public static final int CONTENT_TYPE_OFFSET = VERSION_OFFSET + VERSION_LENGTH;
  public static final int KEY_LENGTH_OFFSET = CONTENT_TYPE_OFFSET + CONTENT_TYPE_LENGTH;
  public static final int KEY_OFFSET = KEY_LENGTH_OFFSET + KEY_LENGTH_SIZE;

  private EventBridgeRecord() {}

  public static int recordLength(final int keyLength, final int payloadLength) {
    return VERSION_LENGTH
        + CONTENT_TYPE_LENGTH
        + KEY_LENGTH_SIZE
        + keyLength
        + Integer.BYTES
        + payloadLength;
  }

  public static int write(
      final MutableDirectBuffer buffer,
      final int offset,
      final ContentType contentType,
      final DirectBuffer key,
      final int keyOffset,
      final int keyLength,
      final DirectBuffer payload,
      final int payloadOffset,
      final int payloadLength) {

    int pos = offset;

    buffer.putByte(pos + VERSION_OFFSET, (byte) CURRENT_VERSION);
    buffer.putByte(pos + CONTENT_TYPE_OFFSET, contentType.code());
    buffer.putShort(pos + KEY_LENGTH_OFFSET, (short) keyLength);
    pos += KEY_OFFSET;

    if (keyLength > 0) {
      buffer.putBytes(pos, key, keyOffset, keyLength);
    }
    pos += keyLength;

    buffer.putInt(pos, payloadLength);
    pos += Integer.BYTES;

    if (payloadLength > 0) {
      buffer.putBytes(pos, payload, payloadOffset, payloadLength);
    }
    pos += payloadLength;

    return pos - offset;
  }

  public static int getVersion(final DirectBuffer buffer, final int offset) {
    return buffer.getByte(offset + VERSION_OFFSET) & 0xFF;
  }

  public static ContentType getContentType(final DirectBuffer buffer, final int offset) {
    return ContentType.from(buffer.getByte(offset + CONTENT_TYPE_OFFSET));
  }

  public static int getKeyLength(final DirectBuffer buffer, final int offset) {
    return buffer.getShort(offset + KEY_LENGTH_OFFSET) & 0xFFFF;
  }

  public static int keyOffset(final int recordOffset) {
    return recordOffset + KEY_OFFSET;
  }

  public static int payloadLengthOffset(final int recordOffset, final int keyLength) {
    return recordOffset + KEY_OFFSET + keyLength;
  }

  public static int getPayloadLength(
      final DirectBuffer buffer, final int recordOffset, final int keyLength) {
    return buffer.getInt(payloadLengthOffset(recordOffset, keyLength));
  }

  public static int payloadOffset(final int recordOffset, final int keyLength) {
    return payloadLengthOffset(recordOffset, keyLength) + Integer.BYTES;
  }

  public enum ContentType {
    RAW((byte) 0),
    JSON((byte) 1),
    MSGPACK((byte) 2),
    PROTOBUF((byte) 3),
    AVRO((byte) 4);

    private static final ContentType[] VALUES = values();

    private final byte code;

    ContentType(final byte code) {
      this.code = code;
    }

    public byte code() {
      return code;
    }

    public static ContentType from(final byte code) {
      if (code >= 0 && code < VALUES.length) {
        return VALUES[code];
      }
      return RAW;
    }
  }
}
