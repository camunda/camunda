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

  public AtomixAppendListenerAdapter(final LogStorage.AppendListener delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onWrite(final IndexedRaftLogEntry indexed) {
    delegate.onWrite(
        indexed.index(),
        indexed.isApplicationEntry() ? indexed.getApplicationEntry().highestPosition() : -1);
  }

  @Override
  public void onCommit(final long index, final long highestPosition) {
    delegate.onCommit(index, highestPosition);
  }
}
