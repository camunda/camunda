/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.protocols.raft.storage.log.RaftLogReader;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import io.zeebe.logstreams.spi.ReadResultProcessor;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.LongUnaryOperator;

public class AtomixLogStorageReader implements LogStorageReader {
  private static final ReadResultProcessor DEFAULT_READ_PROCESSOR =
      (buffer, readResult) -> readResult;

  private final RaftLogReader reader;

  AtomixLogStorageReader(final RaftLogReader reader) {
    this.reader = reader;
  }

  @Override
  public long getFirstBlockAddress() {
    return reader.getFirstIndex();
  }

  @Override
  public long read(final ByteBuffer readBuffer, final long address) {
    return read(readBuffer, address, DEFAULT_READ_PROCESSOR);
  }

  @Override
  public long read(
      final ByteBuffer readBuffer, final long address, final ReadResultProcessor processor) {
    if (address < reader.getFirstIndex()) {
      return LogStorage.OP_RESULT_INVALID_ADDR;
    }

    if (address > reader.getLastIndex()) {
      return LogStorage.OP_RESULT_NO_DATA;
    }

    final var result =
        findEntry(address)
            .map(indexed -> copyEntryData(indexed, readBuffer, processor))
            .orElse(LogStorage.OP_RESULT_NO_DATA);

    if (result < 0) {
      return result;
    } else if (result == 0) {
      return LogStorage.OP_RESULT_NO_DATA;
    }

    return reader.getNextIndex();
  }

  @Override
  public long readLastBlock(final ByteBuffer readBuffer, final ReadResultProcessor processor) {
    final var result = read(readBuffer, reader.getLastIndex(), processor);

    // if reading the last index returns invalid address, this means the log is empty
    if (result == LogStorage.OP_RESULT_INVALID_ADDR) {
      return LogStorage.OP_RESULT_NO_DATA;
    }

    return result;
  }

  /**
   * Performs binary search over all known Atomix entries to find the entry containing our position.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public long lookUpApproximateAddress(
      final long position, final LongUnaryOperator positionReader) {
    var low = reader.getFirstIndex();
    var high = reader.getLastIndex();

    if (position == Long.MIN_VALUE) {
      final var maybeEntry = findEntry(reader.getFirstIndex());
      return maybeEntry.map(Indexed::index).orElse(LogStorage.OP_RESULT_INVALID_ADDR);
    }

    // when the log is empty, last index is defined as first index - 1
    if (low >= high) {
      // need a better way to figure out how to know if its empty
      if (findEntry(low).isEmpty()) {
        return LogStorage.OP_RESULT_INVALID_ADDR;
      }

      return low;
    }

    // binary search over index range, assuming we have no missing indexes
    boolean atLeastOneZeebeEntry = false;
    while (low <= high) {
      final var pivotIndex = (low + high) >>> 1;
      final var pivotEntry = findEntry(pivotIndex);

      if (pivotEntry.isPresent()) {
        final Indexed<ZeebeEntry> entry = pivotEntry.get();
        if (position < entry.entry().lowestPosition()) {
          high = pivotIndex - 1;
        } else if (position > entry.entry().highestPosition()) {
          low = pivotIndex + 1;
        } else {
          return pivotIndex;
        }
        atLeastOneZeebeEntry = true;
      } else {
        high = pivotIndex - 1;
      }
    }

    return atLeastOneZeebeEntry
        ? Math.max(high, reader.getFirstIndex())
        : LogStorage.OP_RESULT_NO_DATA;
  }

  @Override
  public void close() {
    reader.close();
  }

  /**
   * Looks up the entry whose index is either the given index, or the closest lower index.
   *
   * @param index index to seek to
   */
  private Optional<Indexed<ZeebeEntry>> findEntry(final long index) {
    if (reader.getCurrentIndex() == index) {
      final var entry = reader.getCurrentEntry();
      if (entry != null && entry.type().equals(ZeebeEntry.class)) {
        return Optional.of(reader.getCurrentEntry().cast());
      }
    }

    if (reader.getNextIndex() != index) {
      reader.reset(index);
    }

    while (reader.hasNext()) {
      final var entry = reader.next();
      if (entry.type().equals(ZeebeEntry.class)) {
        return Optional.of(entry.cast());
      }
    }

    return Optional.empty();
  }

  private long copyEntryData(
      final Indexed<ZeebeEntry> entry, final ByteBuffer dest, final ReadResultProcessor processor) {
    final var data = entry.entry().data();
    final var length = data.remaining();
    if (dest.remaining() < length) {
      return LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
    }

    // old loop to avoid updating the position of the data buffer
    for (int i = 0; i < length; i++) {
      dest.put(data.get(i));
    }

    return processor.process(dest, length);
  }
}
