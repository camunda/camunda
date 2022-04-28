/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.logstreams;

import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Implementation of {@link LogStorage} for the Atomix {@link io.atomix.raft.storage.log.RaftLog}.
 *
 * <p>Note that this class cannot be made final because we currently spy on it in our tests. This
 * should be changed when the log storage implementation is taken out of this module, at which point
 * it can be made final.
 */
public class AtomixLogStorage implements LogStorage, RaftCommitListener {

  private final AtomixReaderFactory readerFactory;
  private final ZeebeLogAppender logAppender;
  private final Set<CommitListener> commitListeners = new CopyOnWriteArraySet<>();

  public AtomixLogStorage(
      final AtomixReaderFactory readerFactory, final ZeebeLogAppender logAppender) {
    this.readerFactory = readerFactory;
    this.logAppender = logAppender;
  }

  public static AtomixLogStorage ofPartition(
      final AtomixReaderFactory readerFactory, final ZeebeLogAppender appender) {
    return new AtomixLogStorage(readerFactory, appender);
  }

  @Override
  public AtomixLogStorageReader newReader() {
    return new AtomixLogStorageReader(readerFactory.create());
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer buffer,
      final AppendListener listener) {
    final var adapter = new AtomixAppendListenerAdapter(lowestPosition, highestPosition, listener);
    logAppender.appendEntry(lowestPosition, highestPosition, buffer, adapter);
  }

  @Override
  public void addCommitListener(final CommitListener listener) {
    commitListeners.add(listener);
  }

  @Override
  public void removeCommitListener(final CommitListener listener) {
    commitListeners.remove(listener);
  }

  @Override
  public void onCommit(final long index) {
    commitListeners.forEach(CommitListener::onCommit);
  }
}
