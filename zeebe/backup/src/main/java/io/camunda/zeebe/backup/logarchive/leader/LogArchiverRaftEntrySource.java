/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLogReader;
import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;

/**
 * Reads committed entries from a committed {@link RaftLogReader}. The frame carries the unchanged
 * serialized journal record and checksum, so that restore rebuilds a byte-identical journal.
 */
public final class LogArchiverRaftEntrySource implements LogArchiverEntrySource {

  private static final long NONE = -1L;

  private final RaftLogReader reader;

  /**
   * @param reader a committed reader, e.g. from {@code RaftPartitionServer#openReader()}; closed
   *     together with this source
   */
  public LogArchiverRaftEntrySource(final RaftLogReader reader) {
    this.reader = reader;
  }

  @Override
  public long firstIndex() {
    return reader.reset();
  }

  @Override
  public void seek(final long index) {
    reader.seek(index);
  }

  @Override
  public boolean hasNext() {
    return reader.hasNext();
  }

  @Override
  public LogArchiveFrame next() {
    return toFrame(reader.next());
  }

  @Override
  public void close() {
    reader.close();
  }

  static LogArchiveFrame toFrame(final IndexedRaftLogEntry entry) {
    final var record = entry.getReplicatableJournalRecord();
    final long lowestPosition;
    final long highestPosition;
    if (entry.isApplicationEntry()) {
      final var applicationEntry = entry.getApplicationEntry();
      lowestPosition = applicationEntry.lowestPosition();
      highestPosition = applicationEntry.highestPosition();
    } else {
      lowestPosition = NONE;
      highestPosition = NONE;
    }
    // RaftLog appends application entries with their lowest position as asqn and every other entry
    // with ASQN_IGNORE (-1). Deriving it here avoids copying the entry's data a second time through
    // getPersistedRaftRecord().
    final long asqn = lowestPosition;
    return new LogArchiveFrame(
        entry.index(),
        entry.term(),
        asqn,
        lowestPosition,
        highestPosition,
        record.checksum(),
        record.serializedJournalRecord());
  }
}
