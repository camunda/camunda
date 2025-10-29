/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import java.io.IOException;

class TestSegment implements FlushableSegment {
  private final long lastIndex;
  private final boolean shouldFlush;
  private final FlushException flushError;
  private boolean flushed;

  TestSegment(final long lastIndex) {
    this(lastIndex, true);
  }

  TestSegment(final long lastIndex, final boolean shouldFlush) {
    this(lastIndex, shouldFlush, null);
  }

  TestSegment(final long lastIndex, final FlushException flushError) {
    this(lastIndex, true, flushError);
  }

  TestSegment(final long lastIndex, final boolean shouldFlush, final FlushException flushError) {
    this.lastIndex = lastIndex;
    this.shouldFlush = shouldFlush;
    this.flushError = flushError;
  }

  @Override
  public long lastIndex() {
    return lastIndex;
  }

  @Override
  public void flush() throws FlushException {
    if (flushError != null) {
      throw flushError;
    }

    flushed = shouldFlush;
    if (!shouldFlush) {
      throw new FlushException(new IOException("flush failed"));
    }
  }
}
