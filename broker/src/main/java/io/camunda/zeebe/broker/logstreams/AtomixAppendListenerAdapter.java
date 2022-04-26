/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.logstreams;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.camunda.zeebe.logstreams.storage.LogStorage;

public record AtomixAppendListenerAdapter(
    LogStorage.AppendListener delegate) implements
    AppendListener {

  @Override
  public void onWrite(final IndexedRaftLogEntry indexed) {
    delegate.onWrite(indexed.index());
  }

  @Override
  public void onWriteError(final Throwable error) {
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
