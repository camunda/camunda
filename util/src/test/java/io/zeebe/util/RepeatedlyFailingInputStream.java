/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Throws an {@link IOException} on every {@code failureFrequency} call to {@link #read}. Otherwise
 * reads the next byte from the {@code underlyingInputStream}.
 */
public final class RepeatedlyFailingInputStream extends InputStream {

  public static final long DEFAULT_FAILURE_FREQUENCY = 8;

  private final InputStream underlyingInputStream;
  private final long failureFrequency;

  private long readCount;

  public RepeatedlyFailingInputStream(final InputStream underlyingInputStream) {
    this(underlyingInputStream, DEFAULT_FAILURE_FREQUENCY);
  }

  public RepeatedlyFailingInputStream(
      final InputStream underlyingInputStream, final long failureFrequency) {
    this.underlyingInputStream = underlyingInputStream;
    this.failureFrequency = failureFrequency;

    readCount = 0;
  }

  @Override
  public int read() throws IOException {
    readCount++;

    if (readCount % failureFrequency == 0) {
      throw new IOException("Read failure - try again");
    } else {
      return underlyingInputStream.read();
    }
  }

  @Override
  public int read(final byte[] b, final int off, final int len) throws IOException {
    readCount = 0;
    return super.read(b, off, len);
  }
}
