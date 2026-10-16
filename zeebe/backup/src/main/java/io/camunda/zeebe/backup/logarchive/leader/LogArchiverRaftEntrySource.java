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
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import org.agrona.DirectBuffer;

/**
 * Reads committed entries from a committed {@link RaftLogReader}. The frame carries the unchanged
 * serialized journal record and checksum, so that restore rebuilds a byte-identical journal.
 *
 * <p>Each entry is also classified as the log archive's own or not (see {@link LogArchiverEntry}):
 * the records of an application entry are decoded just far enough to read their value type, the
 * same way the log stream reader walks a batch.
 */
public final class LogArchiverRaftEntrySource implements LogArchiverEntrySource {

  private static final long NONE = -1L;

  private final RaftLogReader reader;
  // reused to classify entries; only used on the archiver's actor
  private final LoggedEventImpl event = new LoggedEventImpl();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final RecordMetadataDecoder metadataDecoder = new RecordMetadataDecoder();

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
  public LogArchiverEntry next() {
    final var entry = reader.next();
    return new LogArchiverEntry(toFrame(entry), isArchiveOwn(entry));
  }

  @Override
  public void close() {
    reader.close();
  }

  /**
   * Returns true if the entry is not an application entry, or if every record in it has the value
   * type {@code LOG_ARCHIVE}. Anything that cannot be decoded counts as not the archive's own, so
   * that it is archived eagerly, as before.
   */
  boolean isArchiveOwn(final IndexedRaftLogEntry entry) {
    if (!entry.isApplicationEntry()) {
      return true;
    }
    if (!(entry.getApplicationEntry() instanceof final SerializedApplicationEntry serialized)) {
      return false;
    }
    try {
      return containsOnlyLogArchiveRecords(serialized.data());
    } catch (final RuntimeException e) {
      return false;
    }
  }

  private boolean containsOnlyLogArchiveRecords(final DirectBuffer data) {
    if (data.capacity() == 0) {
      return false;
    }
    int offset = 0;
    while (offset < data.capacity()) {
      event.wrap(data, offset);
      final int metadataOffset = event.getMetadataOffset();
      headerDecoder.wrap(data, metadataOffset);
      metadataDecoder.wrap(
          data,
          metadataOffset + headerDecoder.encodedLength(),
          headerDecoder.blockLength(),
          headerDecoder.version());
      if (metadataDecoder.valueType() != ValueType.LOG_ARCHIVE) {
        return false;
      }
      final int length = event.getLength();
      if (length <= 0) {
        return false;
      }
      offset += length;
    }
    return true;
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
