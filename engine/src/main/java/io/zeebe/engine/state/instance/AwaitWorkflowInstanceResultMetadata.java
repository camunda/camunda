/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;

import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class AwaitWorkflowInstanceResultMetadata implements DbValue {

  private long requestId;
  private int requestStreamId;

  public AwaitWorkflowInstanceResultMetadata() {}

  public AwaitWorkflowInstanceResultMetadata(long requestId, int requestStreamId) {
    this.requestId = requestId;
    this.requestStreamId = requestStreamId;
  }

  public long getRequestId() {
    return requestId;
  }

  public AwaitWorkflowInstanceResultMetadata setRequestId(long requestId) {
    this.requestId = requestId;
    return this;
  }

  public int getRequestStreamId() {
    return requestStreamId;
  }

  public AwaitWorkflowInstanceResultMetadata setRequestStreamId(int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    final int startOffset = offset;
    requestId = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    requestStreamId = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    assert (offset - startOffset) == length : "End offset differs from length";
  }

  @Override
  public int getLength() {
    return Long.BYTES + Integer.BYTES;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    final int startOffset = offset;

    buffer.putLong(offset, requestId, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putInt(offset, requestStreamId, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    assert (offset - startOffset) == getLength() : "End offset differs from getLength()";
  }
}
