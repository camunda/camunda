/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstream;

import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges the Atomix RAFT log ({@link ZeebeLogAppender}) to the {@link LogStorage} interface
 * expected by the Zeebe LogStream. Mirrors {@code AtomixLogStorage} from the zeebe-broker module
 * but lives in the Event Bridge so it does not depend on the full broker classpath.
 *
 * <p>Commit notifications from RAFT ({@link RaftCommitListener#onCommit(long)}) are forwarded to
 * all registered {@link CommitListener}s.
 */
public final class EventBridgeLogStorage implements LogStorage, RaftCommitListener {

  private static final Logger LOG = LoggerFactory.getLogger(EventBridgeLogStorage.class);

  private final Supplier<RaftLogReader> readerFactory;
  private final ZeebeLogAppender logAppender;
  private final Set<CommitListener> commitListeners = new CopyOnWriteArraySet<>();

  public EventBridgeLogStorage(
      final Supplier<RaftLogReader> readerFactory, final ZeebeLogAppender logAppender) {
    this.readerFactory = readerFactory;
    this.logAppender = logAppender;
  }

  @Override
  public LogStorageReader newReader() {
    return new EventBridgeLogStorageReader(readerFactory.get());
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final BufferWriter bufferWriter,
      final LogStorage.AppendListener listener) {
    final var adapter = new AppendListenerAdapter(listener);
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
  public void onCommit(final long index) {
    commitListeners.forEach(CommitListener::onCommit);
  }

  /**
   * Adapts RAFT {@link ZeebeLogAppender.AppendListener} callbacks to the {@link
   * LogStorage.AppendListener} interface.
   */
  private static final class AppendListenerAdapter implements ZeebeLogAppender.AppendListener {

    private final LogStorage.AppendListener delegate;

    AppendListenerAdapter(final LogStorage.AppendListener delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onWrite(final IndexedRaftLogEntry indexed) {
      delegate.onWrite(
          indexed.index(),
          indexed.isApplicationEntry() ? indexed.getApplicationEntry().highestPosition() : -1);
    }

    @Override
    public void onWriteError(final Throwable error) {
      LOG.error("Failed to write entry to RAFT log", error);
    }

    @Override
    public void onCommit(final long index, final long highestPosition) {
      delegate.onCommit(index, highestPosition);
    }

    @Override
    public void onCommitError(final long index, final Throwable error) {
      LOG.error("Failed to commit RAFT log entry at index {}", index, error);
    }
  }

  /**
   * Implements {@link LogStorageReader} over a {@link RaftLogReader}. Mirrors {@code
   * AtomixLogStorageReader} from the zeebe-broker module.
   */
  public static final class EventBridgeLogStorageReader implements LogStorageReader {

    private final RaftLogReader reader;
    private final UnsafeBuffer currentBlock = new UnsafeBuffer(0, 0);
    private final UnsafeBuffer nextBlock = new UnsafeBuffer(0, 0);

    EventBridgeLogStorageReader(final RaftLogReader reader) {
      this.reader = reader;
      reset();
    }

    @Override
    public void seek(final long position) {
      final long bounded = Math.max(0, position);
      reader.seekToAsqn(bounded);
      reset();
      readNextBlock();
    }

    @Override
    public void close() {
      reset();
      reader.close();
    }

    @Override
    public boolean hasNext() {
      return hasNextBlock() || readNextBlock();
    }

    @Override
    public org.agrona.DirectBuffer next() {
      if (!hasNext()) {
        throw new NoSuchElementException("No more log entries");
      }
      currentBlock.wrap(nextBlock);
      nextBlock.wrap(0, 0);
      return currentBlock;
    }

    private boolean hasNextBlock() {
      return nextBlock.addressOffset() != 0;
    }

    private boolean readNextBlock() {
      while (reader.hasNext()) {
        final IndexedRaftLogEntry entry;
        try {
          entry = reader.next();
        } catch (final NoSuchElementException e) {
          return false;
        }
        if (entry.isApplicationEntry()) {
          final var appEntry = (SerializedApplicationEntry) entry.getApplicationEntry();
          nextBlock.wrap(appEntry.data());
          return true;
        }
      }
      return false;
    }

    private void reset() {
      currentBlock.wrap(0, 0);
      nextBlock.wrap(0, 0);
    }
  }
}
