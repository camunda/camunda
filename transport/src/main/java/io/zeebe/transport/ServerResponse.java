/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class ServerResponse implements BufferWriter {
  private final DirectBufferWriter writerAdapter = new DirectBufferWriter();
  private BufferWriter writer;
  private int remoteStreamId;
  private long requestId;

  public ServerResponse writer(final BufferWriter writer) {
    this.writer = writer;
    return this;
  }

  public ServerResponse buffer(final DirectBuffer buffer) {
    return buffer(buffer, 0, buffer.capacity());
  }

  public ServerResponse buffer(final DirectBuffer buffer, final int offset, final int length) {
    return writer(writerAdapter.wrap(buffer, offset, length));
  }

  public ServerResponse remoteStreamId(final int remoteStreamId) {
    this.remoteStreamId = remoteStreamId;
    return this;
  }

  public int getStreamId() {
    return remoteStreamId;
  }

  public ServerResponse reset() {
    remoteStreamId = -1;
    writer = null;
    requestId = -1;

    return this;
  }

  public ServerResponse requestId(final long requestId) {
    this.requestId = requestId;
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

  public long getRequestId() {
    return requestId;
  }
}
