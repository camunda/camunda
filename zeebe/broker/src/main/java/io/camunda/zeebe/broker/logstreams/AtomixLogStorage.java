/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams;

import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.zeebe.CompactionBoundInformer;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.util.buffer.BufferWriter;
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
  private final CompactionBoundInformer compactionBoundInformer;
  private final Set<CommitListener> commitListeners = new CopyOnWriteArraySet<>();

  public AtomixLogStorage(
      final AtomixReaderFactory readerFactory,
      final ZeebeLogAppender logAppender,
      final CompactionBoundInformer compactionBoundInformer) {
    this.readerFactory = readerFactory;
    this.logAppender = logAppender;
    this.compactionBoundInformer = compactionBoundInformer;
  }

  public static AtomixLogStorage ofPartition(
      final AtomixReaderFactory readerFactory,
      final ZeebeLogAppender appender,
      final CompactionBoundInformer compactionBoundInformer) {
    return new AtomixLogStorage(readerFactory, appender, compactionBoundInformer);
  }

  @Override
  public AtomixLogStorageReader newReader() {
    return new AtomixLogStorageReader(readerFactory.create());
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final BufferWriter bufferWriter,
      final AppendListener listener) {
    final var adapter = new AtomixAppendListenerAdapter(listener);
    logAppender.appendEntry(lowestPosition, highestPosition, bufferWriter, adapter);
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
  public void updateCompactionBound(final long compactionBound) {

    compactionBoundInformer.updateCompactionBound(compactionBound);
  }

  @Override
  public void onCommit(final long index) {
    commitListeners.forEach(CommitListener::onCommit);
  }
}
