/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.logstreams;

import io.atomix.raft.RaftException.NoLeader;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import java.util.NoSuchElementException;

public final class AtomixAppendListenerAdapter implements AppendListener {
  private final LogStorage.AppendListener delegate;
  private final long highestPosition;
  private final long lowestPosition;

  public AtomixAppendListenerAdapter(
      final long lowestPosition,
      final long highestPosition,
      final LogStorage.AppendListener delegate) {
    this.lowestPosition = lowestPosition;
    this.highestPosition = highestPosition;
    this.delegate = delegate;
  }

  @Override
  public void onWrite(final IndexedRaftLogEntry indexed) {
    delegate.onWrite(indexed.index());
  }

  @Override
  public void onWriteError(final Throwable error) {
    if (error instanceof NoSuchElementException || error instanceof NoLeader) {
      // Not a failure. It is probably during transition to follower.
      Loggers.LOGSTREAMS_LOGGER.debug(
          "Expected to append block: [lowestPos: {} , highestPos: {}], but failed with {}. This can happen during a leader change.",
          lowestPosition,
          highestPosition,
          error.getMessage(),
          error);
      return;
    }

    delegate.onWriteError(error);
  }

  @Override
  public void onCommit(final IndexedRaftLogEntry indexed) {
    delegate.onCommit(indexed.index());
  }

  @Override
  public void onCommitError(final IndexedRaftLogEntry indexed, final Throwable error) {
    delegate.onCommitError(indexed.index(), error);
  }
}
