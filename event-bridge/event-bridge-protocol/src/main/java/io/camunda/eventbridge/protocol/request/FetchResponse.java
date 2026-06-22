/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request;

import io.camunda.eventbridge.protocol.ErrorCode;
import io.camunda.eventbridge.protocol.FetchResponseDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import org.agrona.DirectBuffer;

/**
 * Decodes a {@code FetchResponse} SBE message. The broker encodes the same wire layout, but streams
 * the {@code data} bytes zero-copy via {@code sendfile}; from the reader's side it is an ordinary
 * SBE message.
 */
public class FetchResponse implements BufferReader {

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final FetchResponseDecoder bodyDecoder = new FetchResponseDecoder();

  private ErrorCode errorCode = ErrorCode.NONE;
  private long firstPosition;
  private long lastPosition = -1;
  private long highWatermark;
  private byte[] data = new byte[0];

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public long getFirstPosition() {
    return firstPosition;
  }

  public long getLastPosition() {
    return lastPosition;
  }

  public long getHighWatermark() {
    return highWatermark;
  }

  public byte[] getData() {
    return data;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    errorCode = bodyDecoder.errorCode();
    firstPosition = bodyDecoder.firstPosition();
    lastPosition = bodyDecoder.lastPosition();
    highWatermark = bodyDecoder.highWatermark();

    final int dataLength = bodyDecoder.dataLength();
    // Guard against a corrupt/forged var-data length before allocating: it cannot exceed the
    // bytes actually available in this message.
    if (dataLength < 0 || dataLength > length) {
      throw new IllegalArgumentException(
          "FetchResponse dataLength " + dataLength + " out of bounds for message length " + length);
    }
    data = new byte[dataLength];
    if (dataLength > 0) {
      bodyDecoder.getData(data, 0, dataLength);
    }
  }
}
