/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import static org.agrona.BitUtil.SIZE_OF_LONG;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RequestResponseHeaderDescriptor {
  public static final int REQUEST_ID_OFFSET;
  public static final int HEADER_LENGTH;

  static {
    int offset = 0;

    REQUEST_ID_OFFSET = offset;
    offset += SIZE_OF_LONG;

    HEADER_LENGTH = offset;
  }

  protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

  public static int framedLength(int messageLength) {
    return HEADER_LENGTH + messageLength;
  }

  public static int headerLength() {
    return HEADER_LENGTH;
  }

  public static int requestIdOffset(int offset) {
    return offset + REQUEST_ID_OFFSET;
  }

  public RequestResponseHeaderDescriptor wrap(DirectBuffer buffer, int offset) {
    this.buffer.wrap(buffer, offset, HEADER_LENGTH);
    return this;
  }

  public RequestResponseHeaderDescriptor requestId(long requestId) {
    buffer.putLong(REQUEST_ID_OFFSET, requestId);
    return this;
  }

  public long requestId() {
    return buffer.getLong(REQUEST_ID_OFFSET);
  }
}
