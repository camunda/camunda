/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class AtomixLogStorageReader implements LogStorageReader {
  private final RaftLogReader reader;
  private final ZeebeIndexMapping zeebeIndexMapping;

  public AtomixLogStorageReader(
      final ZeebeIndexMapping zeebeIndexMapping, final RaftLogReader reader) {
    this.reader = reader;
    this.zeebeIndexMapping = zeebeIndexMapping;
  }

  /**
   * Naive implementation that reads the whole log to check for a {@link ZeebeEntry}. Most of the
   * log should be made of these, so in practice this should be fast enough, however callers should
   * take care when calling this method.
   *
   * <p>The reader will be positioned either at the end of the log, or at the position of the first
   * {@link ZeebeEntry} encountered, such that reading the next entry will return the entry after
   * it.
   *
   * @return true if there are no {@link ZeebeEntry}, false otherwise
   */
  @Override
  public boolean isEmpty() {
    reader.reset();

    while (reader.hasNext()) {
      if (reader.next().type() == ZeebeEntry.class) {
        return false;
      }
    }
    return true;
  }

  @Override
  public long read(final DirectBuffer readBuffer, final long address) {
    if (address < reader.getFirstIndex()) {
      return LogStorage.OP_RESULT_INVALID_ADDR;
    }

    if (address > reader.getLastIndex()) {
      return LogStorage.OP_RESULT_NO_DATA;
    }

    final Optional<Indexed<ZeebeEntry>> maybeEntry = findEntry(address);
    if (maybeEntry.isEmpty()) {
      return LogStorage.OP_RESULT_NO_DATA;
    }

    final Indexed<ZeebeEntry> entry = maybeEntry.get();
    final long serializedRecordsLength = wrapEntryData(entry, readBuffer);

    if (serializedRecordsLength < 0) {
      return serializedRecordsLength;
    }

    // for now assume how indexes increase - in the future we should rewrite how we read the
    // logstream to completely ignore addresses entirely
    return entry.index() + 1;
  }

  /**
   * This is currently a quite slow implementation as Atomix does not support navigating backwards;
   * it would require a refactor there if this is ever too slow.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public long readLastBlock(final DirectBuffer readBuffer) {
    final var firstIndex = reader.getFirstIndex();
    var index = reader.getLastIndex();

    do {
      reader.reset(index);
      if (!reader.hasNext()) {
        break;
      }

      final var indexed = reader.next();
      if (indexed.type() == ZeebeEntry.class) {
        wrapEntryData(indexed.cast(), readBuffer);
        return indexed.index() + 1;
      }

      index--;
    } while (index >= firstIndex);

    return LogStorage.OP_RESULT_NO_DATA;
  }

  /**
   * Performs binary search over all known Atomix entries to find the entry containing our position.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public long lookUpApproximateAddress(final long position) {
    final var low = reader.getFirstIndex();
    final var high = reader.getLastIndex();

    if (position == Long.MIN_VALUE) {
      final var optionalEntry = findEntry(reader.getFirstIndex());
      return optionalEntry.map(Indexed::index).orElse(LogStorage.OP_RESULT_INVALID_ADDR);
    }

    // when the log is empty, the last index is defined as first index - 1
    if (low >= high) {
      // need a better way to figure out how to know if its empty
      if (findEntry(low).isEmpty()) {
        return LogStorage.OP_RESULT_INVALID_ADDR;
      }

      return low;
    }

    final var index = zeebeIndexMapping.lookupPosition(position);
    final long result;
    if (index == -1) {
      result = low;
    } else {
      result = index;
    }

    return result;
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
  public Optional<Indexed<ZeebeEntry>> findEntry(final long index) {
    // in the future, reset/seek to the same index will be a NOOP so we can just call it all the
    // time; right now it's a bit slow, but we will immediately implement the new journal so this
    // is fine
    reader.reset(index);

    while (reader.hasNext()) {
      final var entry = reader.next();
      if (entry.type().equals(ZeebeEntry.class)) {
        return Optional.of(entry.cast());
      }
    }

    return Optional.empty();
  }

  private long wrapEntryData(final Indexed<ZeebeEntry> entry, final DirectBuffer dest) {
    final var data = entry.entry().data();
    final var length = data.remaining();
    dest.wrap(data, data.position(), data.remaining());
    return length;
  }
}
