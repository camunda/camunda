/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/** Pays for every byte read from or skipped in the wrapped stream with a shared limiter. */
final class RateLimitedInputStream extends FilterInputStream {

  private final BackupReadRateLimiter limiter;

  RateLimitedInputStream(final InputStream in, final BackupReadRateLimiter limiter) {
    super(in);
    this.limiter = limiter;
  }

  @Override
  public int read() throws IOException {
    final int value = super.read();
    if (value != -1) {
      pay(1);
    }
    return value;
  }

  @Override
  public int read(final byte[] buffer, final int offset, final int length) throws IOException {
    final int count = super.read(buffer, offset, length);
    if (count > 0) {
      pay(count);
    }
    return count;
  }

  @Override
  public long skip(final long count) throws IOException {
    final long skipped = super.skip(count);
    if (skipped > 0) {
      pay(skipped);
    }
    return skipped;
  }

  private void pay(final long bytes) throws InterruptedIOException {
    try {
      limiter.acquire(bytes);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      final var exception = new InterruptedIOException("Interrupted while limiting backup reads");
      exception.initCause(e);
      throw exception;
    }
  }
}
