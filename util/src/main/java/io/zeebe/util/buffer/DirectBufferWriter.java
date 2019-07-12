/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.buffer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class DirectBufferWriter implements BufferWriter {
  protected DirectBuffer buffer;
  protected int offset;
  protected int length;

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public void write(MutableDirectBuffer writeBuffer, int writeOffset) {
    writeBuffer.putBytes(writeOffset, buffer, offset, length);
  }

  public DirectBufferWriter wrap(DirectBuffer buffer, int offset, int length) {
    this.buffer = buffer;
    this.offset = offset;
    this.length = length;

    return this;
  }

  public DirectBufferWriter wrap(DirectBuffer buffer) {
    return wrap(buffer, 0, buffer.capacity());
  }

  public void reset() {
    buffer = null;
    offset = -1;
    length = 0;
  }

  public static DirectBufferWriter writerFor(DirectBuffer buffer) {
    return new DirectBufferWriter().wrap(buffer);
  }
}
