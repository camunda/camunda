/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.buffer;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DirectBufferReader implements BufferReader {
  protected final UnsafeBuffer readBuffer = new UnsafeBuffer(0, 0);

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    readBuffer.wrap(buffer, offset, length);
  }

  public DirectBuffer getBuffer() {
    return readBuffer;
  }

  public byte[] byteArray() {
    final byte[] array = new byte[readBuffer.capacity()];

    readBuffer.getBytes(0, array);

    return array;
  }
}
