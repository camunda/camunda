/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams;

import io.atomix.raft.RaftCommitListener;
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
  private final AtomixReaderFactory uncommittedReaderFactory;
  private final ZeebeLogAppender logAppender;
  private final Set<CommitListener> commitListeners = new CopyOnWriteArraySet<>();
  private final Set<WriteListener> writeListeners = new CopyOnWriteArraySet<>();

  public AtomixLogStorage(
      final AtomixReaderFactory readerFactory,
      final AtomixReaderFactory uncommittedReaderFactory,
      final ZeebeLogAppender logAppender) {
    this.readerFactory = readerFactory;
    this.uncommittedReaderFactory = uncommittedReaderFactory;
    this.logAppender = logAppender;
  }

  public static AtomixLogStorage ofPartition(
      final AtomixReaderFactory readerFactory,
      final AtomixReaderFactory uncommittedReaderFactory,
      final ZeebeLogAppender appender) {
    return new AtomixLogStorage(readerFactory, uncommittedReaderFactory, appender);
  }

  @Override
  public AtomixLogStorageReader newReader() {
    return new AtomixLogStorageReader(readerFactory.create());
  }

  @Override
  public AtomixLogStorageReader newUncommittedReader() {
    return new AtomixLogStorageReader(uncommittedReaderFactory.create());
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final BufferWriter bufferWriter,
      final AppendListener listener) {
    final var adapter = new AtomixAppendListenerAdapter(new LogStorage.AppendListener() {
      @Override
      public void onWrite(final long index, final long highestPosition) {
        listener.onWrite(index, highestPosition);
        writeListeners.forEach(WriteListener::onWrite);
      }

      @Override
      public void onCommit(final long index, final long highestPosition) {
        listener.onCommit(index, highestPosition);
      }
    });
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
  public void addWriteListener(final WriteListener listener) {
    writeListeners.add(listener);
  }

  @Override
  public void removeWriteListener(final WriteListener listener) {
    writeListeners.remove(listener);
  }

  @Override
  public void onCommit(final long index) {
    commitListeners.forEach(CommitListener::onCommit);
  }
}
