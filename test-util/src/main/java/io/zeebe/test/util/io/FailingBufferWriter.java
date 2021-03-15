/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.io;

import io.zeebe.util.buffer.BufferWriter;
import org.agrona.MutableDirectBuffer;

public final class FailingBufferWriter implements BufferWriter {
  @Override
  public int getLength() {
    return 10;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    throw new FailingBufferWriterException("Could not write - expected");
  }

  public static class FailingBufferWriterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FailingBufferWriterException(final String string) {
      super(string);
    }
  }
}
