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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ListLogStorage implements LogStorage {

  private final ConcurrentNavigableMap<Long, Integer> positionIndexMapping;
  private final ConcurrentSkipListMap<Integer, Entry> entries;
  private LongConsumer positionListener;
  private final Set<CommitListener> commitListeners = new HashSet<>();
  private final List<ListLogStorageReader> listLogStorageReaders;
  private final AtomicInteger currentIndex = new AtomicInteger(0);

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
    final ListLogStorageReader listLogStorageReader = new ListLogStorageReader();
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

    if (positionListener != null) {
      positionListener.accept(highestPosition);
    }
    listener.onCommit(index, highestPosition);
    commitListeners.forEach(CommitListener::onCommit);
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
    // no-op
  }

  public void reset() {
    final Integer lastIndex =
        listLogStorageReaders.stream().map(r -> r.currentIndex).min(Integer::compareTo).orElse(0);
    entries.headMap(lastIndex).clear();
  }

  private record Entry(ByteBuffer data) {}

  private final class ListLogStorageReader implements LogStorageReader {
    volatile int currentIndex;

    @Override
    public void seek(final long position) {
      currentIndex =
          Optional.ofNullable(positionIndexMapping.lowerEntry(position))
              .map(Map.Entry::getValue)
              //              .map(index -> index - 1)
              .orElse(0);
    }

    @Override
    public void close() {
      listLogStorageReaders.remove(this);
    }

    @Override
    public boolean hasNext() {
      return currentIndex >= 0
          && !entries.tailMap(currentIndex).isEmpty(); // && currentIndex < entries.size();
    }

    @Override
    public DirectBuffer next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      final int index = currentIndex;
      currentIndex++;

      return new UnsafeBuffer(entries.get(index).data);
    }
  }
}
