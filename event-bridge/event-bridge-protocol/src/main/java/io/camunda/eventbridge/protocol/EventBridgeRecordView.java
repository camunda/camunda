/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import io.camunda.eventbridge.protocol.EventBridgeRecord.ContentType;
import org.agrona.DirectBuffer;

/**
 * Flyweight reader over an EventBridge event record. Zero allocation — wraps the buffer without
 * copying.
 *
 * <p>Reusable — call {@link #wrap} per record. Caches derived offsets on each wrap.
 */
public final class EventBridgeRecordView {

  private DirectBuffer buffer;
  private int offset;

  private int keyLength;
  private int keyOffset;
  private int payloadLength;
  private int payloadOffset;

  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    this.buffer = buffer;
    this.offset = offset;

    keyLength = EventBridgeRecord.getKeyLength(buffer, offset);
    keyOffset = EventBridgeRecord.keyOffset(offset);
    payloadLength = EventBridgeRecord.getPayloadLength(buffer, offset, keyLength);
    payloadOffset = EventBridgeRecord.payloadOffset(offset, keyLength);
  }

  public int getVersion() {
    return EventBridgeRecord.getVersion(buffer, offset);
  }

  public ContentType getContentType() {
    return EventBridgeRecord.getContentType(buffer, offset);
  }

  public int getKeyLength() {
    return keyLength;
  }

  public DirectBuffer getKeyBuffer() {
    return buffer;
  }

  public int getKeyOffset() {
    return keyOffset;
  }

  public boolean hasKey() {
    return keyLength > 0;
  }

  public String getKeyAsString() {
    if (keyLength == 0) {
      return "";
    }
    final var bytes = new byte[keyLength];
    buffer.getBytes(keyOffset, bytes);
    return new String(bytes);
  }

  public void getKeyBytes(final byte[] dest, final int destOffset) {
    buffer.getBytes(keyOffset, dest, destOffset, keyLength);
  }

  public int getPayloadLength() {
    return payloadLength;
  }

  public DirectBuffer getPayloadBuffer() {
    return buffer;
  }

  public int getPayloadOffset() {
    return payloadOffset;
  }

  public byte[] getPayloadAsBytes() {
    final var bytes = new byte[payloadLength];
    buffer.getBytes(payloadOffset, bytes);
    return bytes;
  }

  public void getPayloadBytes(final byte[] dest, final int destOffset) {
    buffer.getBytes(payloadOffset, dest, destOffset, payloadLength);
  }

  public int getRecordLength() {
    return EventBridgeRecord.recordLength(keyLength, payloadLength);
  }
}
