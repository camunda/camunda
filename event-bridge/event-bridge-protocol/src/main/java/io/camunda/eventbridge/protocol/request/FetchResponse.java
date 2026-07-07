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
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Decodes a {@code FetchResponse} SBE message. The broker encodes the same wire layout, but streams
 * the {@code data} bytes zero-copy via {@code sendfile}; from the reader's side it is an ordinary
 * SBE message.
 *
 * <p>{@link #getData()} is a window into the wrapped transport buffer, not a copy: it is only valid
 * as long as that buffer is neither reused nor freed. Callers that need the bytes beyond the
 * response lifecycle must copy them out.
 */
public class FetchResponse implements BufferReader {

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final FetchResponseDecoder bodyDecoder = new FetchResponseDecoder();

  private ErrorCode errorCode = ErrorCode.NONE;
  private long firstPosition;
  private long lastPosition = -1;
  private long highWatermark;
  private final UnsafeBuffer data = new UnsafeBuffer(0, 0);

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

  /** Window over the fetched batch bytes inside the wrapped transport buffer. */
  public DirectBuffer getData() {
    return data;
  }

  public int getDataLength() {
    return data.capacity();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    errorCode = bodyDecoder.errorCode();
    firstPosition = bodyDecoder.firstPosition();
    lastPosition = bodyDecoder.lastPosition();
    highWatermark = bodyDecoder.highWatermark();

    final int dataLength = bodyDecoder.dataLength();
    // Guard against a corrupt/forged var-data length before wrapping: it cannot exceed the
    // bytes actually available in this message.
    if (dataLength < 0 || dataLength > length) {
      throw new IllegalArgumentException(
          "FetchResponse dataLength " + dataLength + " out of bounds for message length " + length);
    }
    final int dataOffset = bodyDecoder.limit() + FetchResponseDecoder.dataHeaderLength();
    data.wrap(buffer, dataOffset, dataLength);
  }
}
