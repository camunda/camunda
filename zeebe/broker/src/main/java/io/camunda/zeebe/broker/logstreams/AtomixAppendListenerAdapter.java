/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.camunda.zeebe.logstreams.storage.LogStorage;

public final class AtomixAppendListenerAdapter implements AppendListener {
  private final LogStorage.AppendListener delegate;
  private long highestPosition = -1;

  public AtomixAppendListenerAdapter(final LogStorage.AppendListener delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onWrite(final IndexedRaftLogEntry indexed) {
    highestPosition =
        indexed.isApplicationEntry() ? indexed.getApplicationEntry().highestPosition() : -1;
    delegate.onWrite(indexed.index(), highestPosition);
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    delegate.onCommit(index, highestPosition);
  }

  @Override
  public void onCommitError(final long index, final Throwable error) {
    delegate.onCommitError(index, highestPosition, error);
  }
}
