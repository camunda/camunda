/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.io;

import java.io.IOException;
import java.io.OutputStream;

/** */
public final class RepeatedlyFailingOutputStream extends OutputStream {
  public static final long DEFAULT_FAILURE_FREQUENCY = 8;

  private final OutputStream underlyingOutputStream;
  private final long failureFrequency;

  private long writeCount;

  public RepeatedlyFailingOutputStream(final OutputStream underlyingOutputStream) {
    this(underlyingOutputStream, DEFAULT_FAILURE_FREQUENCY);
  }

  public RepeatedlyFailingOutputStream(
      final OutputStream underlyingOutputStream, final long failureFrequency) {
    this.underlyingOutputStream = underlyingOutputStream;
    this.failureFrequency = failureFrequency;

    writeCount = 0;
  }

  public OutputStream getUnderlyingOutputStream() {
    return underlyingOutputStream;
  }

  @Override
  public void write(final int b) throws IOException {
    writeCount++;

    if (writeCount % failureFrequency == 0) {
      throw new IOException("Write failure");
    } else {
      underlyingOutputStream.write(b);
    }
  }
}
