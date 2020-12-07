/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import io.atomix.raft.RaftException.NoLeader;
import io.zeebe.logstreams.spi.LogStorage.AppendListener;
import java.util.NoSuchElementException;

public final class Listener implements AppendListener {
  private final LogStorageAppender appender;
  private final long highestPosition;
  private final long startTime;

  public Listener(
      final LogStorageAppender appender, final long highestPosition, final long startTime) {
    this.appender = appender;
    this.highestPosition = highestPosition;
    this.startTime = startTime;
  }

  @Override
  public void onWrite(final long address) {
    appender.notifyWritePosition(highestPosition, startTime);
  }

  @Override
  public void onWriteError(final Throwable error) {
    if (error instanceof NoSuchElementException || error instanceof NoLeader) {
      // Not a failure. It is probably during transition to follower.
      LogStorageAppender.LOG.debug(
          "Failed to append block with last event position {}. This can happen during a leader change.",
          highestPosition,
          error);
      return;
    }

    LogStorageAppender.LOG.error(
        "Failed to append block with last event position {}.", highestPosition, error);
    appender.runOnFailure(error);
  }

  @Override
  public void onCommit(final long address) {
    releaseBackPressure();
    appender.notifyCommitPosition(highestPosition, startTime);
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
