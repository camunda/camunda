/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.sender;

import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;

public class OutgoingMessage {
  private final TransportHeaderWriter headerWriter = new TransportHeaderWriter();

  private final int remoteStreamId;

  private final MutableDirectBuffer buffer;

  private final long deadline;

  public OutgoingMessage(int remoteStreamId, MutableDirectBuffer buffer, long deadline) {
    this.remoteStreamId = remoteStreamId;
    this.buffer = buffer;
    this.deadline = deadline;
  }

  public int getRemoteStreamId() {
    return remoteStreamId;
  }

  public MutableDirectBuffer getBuffer() {
    return buffer;
  }

  public TransportHeaderWriter getHeaderWriter() {
    return headerWriter;
  }

  public ByteBuffer getAllocatedBuffer() {
    return buffer.byteBuffer();
  }

  public long getDeadline() {
    return deadline;
  }
}
