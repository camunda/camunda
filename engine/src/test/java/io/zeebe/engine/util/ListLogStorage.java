/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.LongConsumer;
import org.agrona.DirectBuffer;

public class ListLogStorage implements LogStorage {

  private final ConcurrentNavigableMap<Long, Integer> positionIndexMapping;
  private final List<ZeebeEntry> entries;
  private LongConsumer positionListener;
  private LinkedBlockingQueue<Integer> indexQueue;

  public ListLogStorage() {
    this.entries = new CopyOnWriteArrayList<>();
    this.positionIndexMapping = new ConcurrentSkipListMap<>();
  }

  public void setIndexQueue(final LinkedBlockingQueue<Integer> indexQueue) {
    this.indexQueue = indexQueue;
  }

  public void setPositionListener(final LongConsumer positionListener) {
    this.positionListener = positionListener;
  }

  @Override
  public LogStorageReader newReader() {
    return new LogStorageReader() {
      @Override
      public boolean isEmpty() {
        return entries.isEmpty();
      }

      @Override
      public long read(final DirectBuffer readBuffer, final long address) {
        final var index = (int) (address - 1);

        if (index < 0 || index >= entries.size()) {
          return OP_RESULT_NO_DATA;
        }

        final var entry = entries.get(index);
        final var data = entry.data();
        readBuffer.wrap(data, data.position(), data.remaining());

        return address + 1;
      }

      @Override
      public long readLastBlock(final DirectBuffer readBuffer) {
        return read(readBuffer, entries.size());
      }

      @Override
      public long lookUpApproximateAddress(final long position) {

        if (position == Long.MIN_VALUE) {
          return entries.isEmpty() ? OP_RESULT_INVALID_ADDR : 1;
        }

        final var lowerIndex = positionIndexMapping.lowerEntry(position);
        if (lowerIndex != null) {
          return lowerIndex.getValue();
        }

        return 1;
      }

      @Override
      public void close() {}
    };
  }

  @Override
  public void append(final ByteBuffer blockBuffer, final AppendListener listener) {
    try {
      final var entry = new ZeebeEntry(0, 0, blockBuffer);
      entries.add(entry);
      var index = entries.size();

      if (indexQueue != null && !indexQueue.isEmpty()) {
        index = indexQueue.poll();
      }
      listener.updateRecords(entry, index);
      positionIndexMapping.put((long) (index << 8), index);
      listener.onWrite(index);

      if (positionListener != null) {
        positionListener.accept(entry.highestPosition());
      }
      listener.onCommit(index);
    } catch (final Exception e) {
      listener.onWriteError(e);
    }
  }

  @Override
  public void open() throws IOException {}

  @Override
  public void close() {
    entries.clear();
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public void flush() throws Exception {}
}
