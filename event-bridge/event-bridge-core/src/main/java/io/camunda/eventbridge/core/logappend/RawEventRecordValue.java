/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.logappend;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A {@link UnifiedRecordValue} shim that wraps a raw binary payload so it can be written into the
 * LogStream without coupling to the Zeebe domain record type system.
 *
 * <p>The payload is encoded as a single msgpack binary value. This bypasses the msgpack property
 * machinery of {@link io.camunda.zeebe.msgpack.value.ObjectValue}.
 *
 * <p><strong>Write path:</strong> call {@link #wrapPayload(DirectBuffer, int, int)} or {@link
 * #wrapPayload(byte[])} to set the raw bytes, then use the instance as a {@link
 * io.camunda.zeebe.logstreams.log.LogAppendEntry} value.
 *
 * <p><strong>Read path:</strong> call {@link #wrap(DirectBuffer, int, int)} with a buffer
 * containing the msgpack-encoded blob (as stored in the LogStream). The method decodes the msgpack
 * binary header and makes the raw payload available via {@link #getRawPayload()}.
 */
public final class RawEventRecordValue extends UnifiedRecordValue {

  private final MsgPackWriter msgPackWriter = new MsgPackWriter();
  private transient MsgPackReader msgPackReader;

  private DirectBuffer rawPayload = new UnsafeBuffer(0, 0);
  private int rawPayloadOffset;
  private int rawPayloadLength;

  public RawEventRecordValue() {
    super(0);
  }

  /**
   * Sets the raw event payload bytes to be written into the log (write path).
   *
   * @param buffer buffer containing the raw payload bytes
   * @param offset offset into {@code buffer}
   * @param length number of bytes to read from {@code buffer}
   */
  public void wrapPayload(final DirectBuffer buffer, final int offset, final int length) {
    rawPayload = buffer;
    rawPayloadOffset = offset;
    rawPayloadLength = length;
  }

  /**
   * Sets the raw event payload bytes from a byte array (write path).
   *
   * @param bytes the raw event payload
   */
  public void wrapPayload(final byte[] bytes) {
    rawPayload = new UnsafeBuffer(bytes);
    rawPayloadOffset = 0;
    rawPayloadLength = bytes.length;
  }

  /**
   * Deserializes from a msgpack-encoded buffer (read path). Called by {@link
   * io.camunda.zeebe.logstreams.log.LoggedEvent#readValue(io.camunda.zeebe.util.buffer.BufferReader)}
   * when reading events back from the LogStream.
   *
   * <p>The buffer is expected to contain the output of {@link #write(MutableDirectBuffer, int)}: a
   * single msgpack binary value (BIN8/BIN16/BIN32 header followed by the raw payload bytes). After
   * this call, the raw payload is available via {@link #getRawPayload()}.
   *
   * @param buffer buffer holding the msgpack-encoded value
   * @param offset start offset within {@code buffer}
   * @param length total length of the encoded value (including msgpack header)
   */
  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    if (msgPackReader == null) {
      msgPackReader = new MsgPackReader();
    }
    msgPackReader.wrap(buffer, offset, length);
    final int payloadLength = msgPackReader.readBinaryLength();
    rawPayload = buffer;
    // msgPackReader.getOffset() is relative to the slice start (offset), so add offset
    // to obtain the absolute position of the raw payload bytes in the original buffer.
    rawPayloadOffset = offset + msgPackReader.getOffset();
    rawPayloadLength = payloadLength;
  }

  @Override
  public int getLength() {
    // msgpack bin header (2-5 bytes) + payload length
    return msgpackBinaryEncodedLength(rawPayloadLength);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    msgPackWriter.wrap(buffer, offset);
    msgPackWriter.writeBinary(rawPayload, rawPayloadOffset, rawPayloadLength);
    return getLength();
  }

  /**
   * Returns the raw payload buffer. Valid after either {@link #wrapPayload(DirectBuffer, int, int)}
   * (write path) or {@link #wrap(DirectBuffer, int, int)} (read path).
   */
  public DirectBuffer getRawPayload() {
    return rawPayload;
  }

  /** Returns the offset of the raw payload bytes within {@link #getRawPayload()}. */
  public int getRawPayloadOffset() {
    return rawPayloadOffset;
  }

  /** Returns the length of the raw payload bytes. */
  public int getRawPayloadLength() {
    return rawPayloadLength;
  }

  /**
   * Returns the encoded byte length for a msgpack binary value of {@code dataLength} bytes,
   * including the header.
   */
  private static int msgpackBinaryEncodedLength(final int dataLength) {
    if (dataLength <= 0xFF) {
      return 2 + dataLength; // bin8: 0xc4 + 1-byte length
    } else if (dataLength <= 0xFFFF) {
      return 3 + dataLength; // bin16: 0xc5 + 2-byte length
    } else {
      return 5 + dataLength; // bin32: 0xc6 + 4-byte length
    }
  }
}
