/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.allocation;

import io.zeebe.util.CloseableSilently;
import java.nio.ByteBuffer;

public abstract class AllocatedBuffer implements CloseableSilently {

  protected ByteBuffer rawBuffer;
  private volatile boolean closed;

  public AllocatedBuffer(final ByteBuffer buffer) {
    rawBuffer = buffer;
    closed = false;
  }

  public ByteBuffer getRawBuffer() {
    return rawBuffer;
  }

  public int capacity() {
    return rawBuffer.capacity();
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      doClose();
      rawBuffer = null;
    }
  }

  public void doClose() {}
}
