/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.atomix.storage.journal.Indexed;
import io.zeebe.logstreams.spi.LogStorage;

public final class AtomixAppendListenerAdapter implements AppendListener {
  private final LogStorage.AppendListener delegate;

  public AtomixAppendListenerAdapter(final LogStorage.AppendListener delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onWrite(final Indexed<ZeebeEntry> indexed) {
    delegate.onWrite(indexed.index());
  }

  @Override
  public void updateRecords(ZeebeEntry entry, long index) {
    delegate.updateRecords(entry, index);
  }

  @Override
  public void onWriteError(final Throwable error) {
    delegate.onWriteError(error);
  }

  @Override
  public void onCommit(final Indexed<ZeebeEntry> indexed) {
    delegate.onCommit(indexed.index());
  }

  @Override
  public void onCommitError(final Indexed<ZeebeEntry> indexed, final Throwable error) {
    delegate.onCommitError(indexed.index(), error);
  }
}
