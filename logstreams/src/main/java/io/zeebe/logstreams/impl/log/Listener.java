/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import io.zeebe.logstreams.spi.LogStorage.AppendListener;
import java.util.NoSuchElementException;

public final class Listener implements AppendListener {

  private final LogStorageAppender appender;
  private final long highestPosition;

  public Listener(final LogStorageAppender appender, final long highestPosition) {
    this.appender = appender;
    this.highestPosition = highestPosition;
  }

  @Override
  public void onWrite(final long address) {}

  @Override
  public void onWriteError(final Throwable error) {
    LogStorageAppender.LOG.error(
        "Failed to append block with last event position {}.", highestPosition, error);
    if (error instanceof NoSuchElementException) {
      // Not a failure. It is probably during transition to follower.
      return;
    }

    appender.runOnFailure(error);
  }

  @Override
  public void onCommit(final long address) {
    releaseBackPressure();
  }

  @Override
  public void onCommitError(final long address, final Throwable error) {
    LogStorageAppender.LOG.error(
        "Failed to commit block with last event position {}.", highestPosition, error);
    releaseBackPressure();
    appender.runOnFailure(error);
  }

  private void releaseBackPressure() {
    appender.releaseBackPressure(highestPosition);
  }
}
