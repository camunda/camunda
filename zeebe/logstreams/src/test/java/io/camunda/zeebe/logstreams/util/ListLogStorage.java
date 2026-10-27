/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class ListLogStorage implements LogStorage {

  private final ConcurrentNavigableMap<Long, Integer> positionIndexMapping;
  private final ConcurrentSkipListMap<Integer, Entry> entries;
  private @Nullable LongConsumer positionListener;
  // Appends and commits notify listeners from the appending thread while tests add and remove
  // listeners from their own thread, so these sets must tolerate concurrent iteration and mutation.
  private final Set<CommitListener> commitListeners = new CopyOnWriteArraySet<>();
  private final Set<CommittedPositionListener> committedPositionListeners =
      new CopyOnWriteArraySet<>();
  private final Set<AppendedListener> appendedListeners = new CopyOnWriteArraySet<>();
  private final Queue<Runnable> pendingCommits = new ConcurrentLinkedQueue<>();
  private final List<ListLogStorageReader> listLogStorageReaders;
  private final AtomicInteger currentIndex = new AtomicInteger(0);
  private final AtomicInteger committedIndex = new AtomicInteger(-1);
  private volatile boolean deferCommits;

  public ListLogStorage() {
    entries = new ConcurrentSkipListMap<Integer, Entry>();
    positionIndexMapping = new ConcurrentSkipListMap<>();
    listLogStorageReaders = new CopyOnWriteArrayList<>();
  }

  public void setPositionListener(final LongConsumer positionListener) {
    this.positionListener = positionListener;
  }

  @Override
  public LogStorageReader newReader() {
    final ListLogStorageReader listLogStorageReader = new ListLogStorageReader(true);
    listLogStorageReaders.add(listLogStorageReader);
    return listLogStorageReader;
  }

  @Override
  public LogStorageReader newUncommittedReader() {
    final ListLogStorageReader listLogStorageReader = new ListLogStorageReader(false);
    listLogStorageReaders.add(listLogStorageReader);
    return listLogStorageReader;
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final BufferWriter bufferWriter,
      final AppendListener listener) {
    final var buffer = ByteBuffer.allocate(bufferWriter.getLength());
    bufferWriter.write(new UnsafeBuffer(buffer), 0);
    append(lowestPosition, highestPosition, buffer, listener);
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer blockBuffer,
      final AppendListener listener) {
    final var entry = new Entry(blockBuffer);
    final var index = currentIndex.getAndIncrement();
    entries.put(index, entry);
    positionIndexMapping.put(lowestPosition, index);
    listener.onWrite(index, highestPosition);
    appendedListeners.forEach(appendedListener -> appendedListener.onAppend(highestPosition));

    if (positionListener != null) {
      positionListener.accept(highestPosition);
    }
    final Runnable commit =
        () -> {
          committedIndex.set(index);
          listener.onCommit(index, highestPosition);
          commitListeners.forEach(CommitListener::onCommit);
          committedPositionListeners.forEach(
              positionListener -> positionListener.onCommittedPosition(highestPosition));
        };
    if (deferCommits) {
      pendingCommits.add(commit);
    } else {
      commit.run();
    }
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

  public void deferCommits() {
    deferCommits = true;
  }

  public int pendingCommitCount() {
    return pendingCommits.size();
  }

  public void commitPendingEntries() {
    Runnable pendingCommit;
    while ((pendingCommit = pendingCommits.poll()) != null) {
      pendingCommit.run();
    }
  }

  public void reset() {
    final Integer lastIndex =
        listLogStorageReaders.stream()
            .map(r -> r.currentIndex.get())
            .min(Integer::compareTo)
            .orElse(0);
    entries.headMap(lastIndex).clear();
  }

  private record Entry(ByteBuffer data) {}

  private final class ListLogStorageReader implements LogStorageReader {
    final AtomicInteger currentIndex = new AtomicInteger(0);
    private final boolean committedOnly;

    private ListLogStorageReader(final boolean committedOnly) {
      this.committedOnly = committedOnly;
    }

    @Override
    public void seek(final long position) {
      currentIndex.set(
          Optional.ofNullable(positionIndexMapping.lowerEntry(position))
              .map(Map.Entry::getValue)
              .orElse(0));
    }

    @Override
    public void close() {
      listLogStorageReaders.remove(this);
    }

    @Override
    public boolean hasNext() {
      final var index = currentIndex.get();
      if (committedOnly && index > committedIndex.get()) {
        return false;
      }
      return index >= 0 && !entries.tailMap(index).isEmpty();
    }

    @Override
    public DirectBuffer next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      final var entry = entries.get(currentIndex.get());
      if (entry == null) {
        throw new NoSuchElementException();
      }
      final var buffer = new UnsafeBuffer(entry.data);
      currentIndex.incrementAndGet();
      return buffer;
    }
  }
}
