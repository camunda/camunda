/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import io.camunda.eventbridge.protocol.EventBridgeRecord.ContentType;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Builds an EventBridge event record as a byte array. Used by the client SDK to construct
 * individual entries before adding them to an {@link EventBridgeBatchBuilder}.
 */
public final class EventBridgeRecordBuilder {

  private ContentType contentType = ContentType.RAW;
  private byte[] key;
  private int keyOffset;
  private int keyLength;
  private byte[] payload;
  private int payloadOffset;
  private int payloadLength;

  public EventBridgeRecordBuilder contentType(final ContentType contentType) {
    this.contentType = contentType;
    return this;
  }

  public EventBridgeRecordBuilder key(final byte[] key) {
    return key(key, 0, key.length);
  }

  public EventBridgeRecordBuilder key(final byte[] key, final int offset, final int length) {
    this.key = key;
    keyOffset = offset;
    keyLength = length;
    return this;
  }

  public EventBridgeRecordBuilder key(final String key) {
    return key(key.getBytes());
  }

  public EventBridgeRecordBuilder payload(final byte[] payload) {
    return payload(payload, 0, payload.length);
  }

  public EventBridgeRecordBuilder payload(
      final byte[] payload, final int offset, final int length) {
    this.payload = payload;
    payloadOffset = offset;
    payloadLength = length;
    return this;
  }

  public byte[] build() {
    final int kLen = key != null ? keyLength : 0;
    final int pLen = payload != null ? payloadLength : 0;
    final int totalLength = EventBridgeRecord.recordLength(kLen, pLen);

    final var output = new byte[totalLength];
    final var buffer = new UnsafeBuffer(output);
    final var keyBuf = key != null ? new UnsafeBuffer(key) : new UnsafeBuffer(0, 0);
    final var payloadBuf = payload != null ? new UnsafeBuffer(payload) : new UnsafeBuffer(0, 0);

    EventBridgeRecord.write(
        buffer, 0, contentType, keyBuf, keyOffset, kLen, payloadBuf, payloadOffset, pLen);

    return output;
  }

  public EventBridgeRecordBuilder reset() {
    contentType = ContentType.RAW;
    key = null;
    keyOffset = 0;
    keyLength = 0;
    payload = null;
    payloadOffset = 0;
    payloadLength = 0;
    return this;
  }
}
