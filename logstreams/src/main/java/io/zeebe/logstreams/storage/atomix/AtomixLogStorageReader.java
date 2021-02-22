/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.storage.log.Indexed;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class AtomixLogStorageReader implements LogStorageReader {
  private final RaftLogReader reader;

  public AtomixLogStorageReader(final RaftLogReader reader) {
    this.reader = reader;
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
    // although seemingly inefficient, the log will contain mostly ZeebeEntry entries and a few
    // InitialEntry, so this should be rather fast in practice
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

  @Override
  public long readLastBlock(final DirectBuffer readBuffer) {
    try {
      reader.seekToAsqn(Long.MAX_VALUE);
    } catch (final UnsupportedOperationException e) {
      // tried to seek to an uncommitted ASQN, which should almost never happen
      return findLastZeebeEntry(readBuffer);
    }

    if (reader.hasNext()) {
      final Indexed<RaftLogEntry> entry = reader.next();
      if (entry.type() == ZeebeEntry.class) {
        wrapEntryData(entry.cast(), readBuffer);
        return entry.index() + 1;
      }
    }

    return LogStorage.OP_RESULT_NO_DATA;
  }

  /**
   * Performs binary search over all known Atomix entries to find the entry containing our position.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public long lookUpApproximateAddress(final long position) {
    if (position == Long.MIN_VALUE) {
      return seekToFirst();
    }

    final long address;
    try {
      address = reader.seekToAsqn(position);
    } catch (final UnsupportedOperationException e) {
      // in case we seek to an unknown position
      return LogStorage.OP_RESULT_INVALID_ADDR;
    }

    if (!reader.hasNext()) {
      return LogStorage.OP_RESULT_INVALID_ADDR;
    }

    return address;
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
    final long nextIndex = reader.reset(index);

    if (nextIndex < index) {
      return Optional.empty();
    }

    while (reader.hasNext()) {
      final var entry = reader.next();
      if (entry.type().equals(ZeebeEntry.class)) {
        return Optional.of(entry.cast());
      }
    }

    return Optional.empty();
  }

  private long seekToFirst() {
    // looking for ASQN 0 is a trick which will skip any initial records with ASQN_IGNORE
    // but stop as soon as we find an ASQN with 0 or greater (and we typically start at 1)
    return findEntry(0).map(Indexed::index).orElse(LogStorage.OP_RESULT_INVALID_ADDR);
  }

  /** @deprecated should be dropped and handled properly by the RaftLogReader or the journal */
  @Deprecated(since = "1.0.0")
  private long findLastZeebeEntry(final DirectBuffer readBuffer) {
    long index = reader.seekToLast();
    long lastReadIndex = -1;

    // to detect we seeked back to the beginning, check if we're reading the same
    // index again
    while (index != lastReadIndex) {
      if (reader.hasNext()) {
        final Indexed<RaftLogEntry> indexed = reader.next();
        if (indexed.type() == ZeebeEntry.class) {
          wrapEntryData(indexed.cast(), readBuffer);
          return indexed.index() + 1;
        }
      }

      lastReadIndex = index;
      index = reader.reset(index - 1);
    }

    return LogStorage.OP_RESULT_NO_DATA;
  }

  private long wrapEntryData(final Indexed<ZeebeEntry> entry, final DirectBuffer dest) {
    final var data = entry.entry().data();
    final var length = data.remaining();
    dest.wrap(data, data.position(), data.remaining());
    return length;
  }
}
