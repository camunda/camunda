/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.transport.impl;

import io.zeebe.transport.ServerResponse;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class ServerResponseImpl implements ServerResponse {
  private final DirectBufferWriter writerAdapter = new DirectBufferWriter();
  private BufferWriter writer;
  private int partitionId;
  private long requestId;

  public ServerResponseImpl writer(final BufferWriter writer) {
    this.writer = writer;
    return this;
  }

  public ServerResponseImpl buffer(final DirectBuffer buffer) {
    return buffer(buffer, 0, buffer.capacity());
  }

  public ServerResponseImpl buffer(final DirectBuffer buffer, final int offset, final int length) {
    return writer(writerAdapter.wrap(buffer, offset, length));
  }

  public ServerResponseImpl reset() {
    partitionId = -1;
    writer = null;
    requestId = -1;

    return this;
  }

  @Override
  public int getLength() {
    return writer.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    writer.write(buffer, offset);
  }

  public BufferWriter getWriter() {
    return writer;
  }

  @Override
  public long getRequestId() {
    return requestId;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ServerResponseImpl setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public ServerResponseImpl setRequestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }
}
