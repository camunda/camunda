/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams;

import io.atomix.raft.RaftApplicationEntryCommittedPositionListener;
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
  private final Set<CommittedPositionListener> committedPositionListeners =
      new CopyOnWriteArraySet<>();
  private final Set<AppendedListener> appendedListeners = new CopyOnWriteArraySet<>();

  /**
   * Raft notifies committed indexes and committed application entry positions through two separate
   * listeners with the same method signature, so this cannot be a second interface implemented by
   * this class.
   */
  private final RaftApplicationEntryCommittedPositionListener committedPositionNotifier =
      this::onCommittedPosition;

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
    final var adapter =
        new AtomixAppendListenerAdapter(
            new AppendListener() {
              @Override
              public void onWrite(final long index, final long highestPosition) {
                listener.onWrite(index, highestPosition);
                appendedListeners.forEach(l -> l.onAppend(highestPosition));
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
  public void addCommittedPositionListener(final CommittedPositionListener listener) {
    committedPositionListeners.add(listener);
  }

  @Override
  public void removeCommittedPositionListener(final CommittedPositionListener listener) {
    committedPositionListeners.remove(listener);
  }

  @Override
  public void addAppendedListener(final AppendedListener listener) {
    appendedListeners.add(listener);
  }

  @Override
  public void removeAppendedListener(final AppendedListener listener) {
    appendedListeners.remove(listener);
  }

  /**
   * Notified by Raft on every role, whenever the commit index advances. This is the only commit
   * signal a follower receives, because it never appends entries itself.
   */
  @Override
  public void onCommit(final long index) {
    commitListeners.forEach(CommitListener::onCommit);
  }

  /**
   * The listener to register for committed application entry positions. Raft only notifies it on
   * the leader, and only for entries the leader appended itself.
   */
  public RaftApplicationEntryCommittedPositionListener committedPositionNotifier() {
    return committedPositionNotifier;
  }

  private void onCommittedPosition(final long highestPosition) {
    committedPositionListeners.forEach(listener -> listener.onCommittedPosition(highestPosition));
  }
}
