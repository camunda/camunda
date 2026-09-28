/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * In-memory log storage for the scratch engine. Every block is committed as soon as it is appended,
 * and blocks that every reader has moved past are dropped. A reader that falls more than {@link
 * #MAX_RETAINED_BLOCKS} behind loses the blocks it has not read yet, which bounds memory even if a
 * reader is never closed.
 */
@NullMarked
final class ScratchLogStorage implements LogStorage {

  static final int MAX_RETAINED_BLOCKS = 10_000;

  private final ConcurrentSkipListMap<Long, DirectBuffer> blocks = new ConcurrentSkipListMap<>();
  private final ConcurrentSkipListMap<Long, Long> lowestPositionToIndex =
      new ConcurrentSkipListMap<>();
  private final Set<Reader> readers = new CopyOnWriteArraySet<>();
  private final Set<CommitListener> commitListeners = new CopyOnWriteArraySet<>();
  private final Set<CommittedPositionListener> committedPositionListeners =
      new CopyOnWriteArraySet<>();
  private final Set<AppendedListener> appendedListeners = new CopyOnWriteArraySet<>();
  private final AtomicLong nextIndex = new AtomicLong();

  @Override
  public LogStorageReader newReader() {
    return newUncommittedReader();
  }

  @Override
  public LogStorageReader newUncommittedReader() {
    final var reader = new Reader();
    readers.add(reader);
    return reader;
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final BufferWriter bufferWriter,
      final AppendListener listener) {
    final var block = new UnsafeBuffer(new byte[bufferWriter.getLength()]);
    bufferWriter.write(block, 0);

    final var index = nextIndex.getAndIncrement();
    blocks.put(index, block);
    lowestPositionToIndex.put(lowestPosition, index);
    trim();

    listener.onWrite(index, highestPosition);
    appendedListeners.forEach(l -> l.onAppend(highestPosition));
    listener.onCommit(index, highestPosition);
    commitListeners.forEach(CommitListener::onCommit);
    committedPositionListeners.forEach(l -> l.onCommittedPosition(highestPosition));
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

  int retainedBlocks() {
    return blocks.size();
  }

  private void trim() {
    final long appended = nextIndex.get();
    long keepFrom = appended;
    for (final var reader : readers) {
      keepFrom = Math.min(keepFrom, reader.nextIndex);
    }
    keepFrom = Math.max(keepFrom, appended - MAX_RETAINED_BLOCKS);

    blocks.headMap(keepFrom).clear();
    for (var first = lowestPositionToIndex.firstEntry();
        first != null && first.getValue() < keepFrom;
        first = lowestPositionToIndex.firstEntry()) {
      lowestPositionToIndex.remove(first.getKey());
    }
  }

  private final class Reader implements LogStorageReader {
    private volatile long nextIndex;

    @Override
    public void seek(final long position) {
      final Map.Entry<Long, Long> entry = lowestPositionToIndex.floorEntry(position);
      if (entry != null) {
        nextIndex = entry.getValue();
      } else {
        final var first = lowestPositionToIndex.firstEntry();
        nextIndex = first != null ? first.getValue() : ScratchLogStorage.this.nextIndex.get();
      }
    }

    @Override
    public void close() {
      readers.remove(this);
    }

    @Override
    public boolean hasNext() {
      return blocks.containsKey(nextIndex);
    }

    @Override
    public DirectBuffer next() {
      final var block = blocks.get(nextIndex);
      if (block == null) {
        throw new NoSuchElementException();
      }
      nextIndex++;
      return block;
    }
  }
}
