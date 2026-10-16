/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.protocol.ReplicatableJournalRecord;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.logstreams.impl.log.SequencedBatch;
import io.camunda.zeebe.logstreams.impl.serializer.SequencedBatchSerializer;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LogArchiverRaftEntrySourceTest {

  private final RaftLogReader reader = mock(RaftLogReader.class);
  private final LogArchiverRaftEntrySource source = new LogArchiverRaftEntrySource(reader);

  @Test
  void shouldMarkEntryWithOnlyLogArchiveRecordsAsOwn() {
    // given - e.g. an ARCHIVE_LOG command, or a LOG_ARCHIVED event with its follow-ups
    final var entry = applicationEntry(ValueType.LOG_ARCHIVE, ValueType.LOG_ARCHIVE);
    when(reader.next()).thenReturn(entry);

    // when
    final var next = source.next();

    // then
    assertThat(next.archiveOwn()).isTrue();
    assertThat(next.frame().index()).isEqualTo(entry.index());
  }

  @Test
  void shouldNotMarkEntryWithOtherRecordsAsOwn() {
    // given
    final var entry = applicationEntry(ValueType.LOG_ARCHIVE, ValueType.CHECKPOINT);
    when(reader.next()).thenReturn(entry);

    // when
    final var next = source.next();

    // then
    assertThat(next.archiveOwn()).isFalse();
  }

  @Test
  void shouldNotMarkEntryStartingWithOtherRecordAsOwn() {
    // given
    final var entry = applicationEntry(ValueType.JOB);
    when(reader.next()).thenReturn(entry);

    // when
    final var next = source.next();

    // then
    assertThat(next.archiveOwn()).isFalse();
  }

  @Test
  void shouldMarkNonApplicationEntryAsOwn() {
    // given - e.g. the initial entry of a new leader
    final var entry = mock(IndexedRaftLogEntry.class);
    when(entry.index()).thenReturn(5L);
    when(entry.term()).thenReturn(2L);
    when(entry.isApplicationEntry()).thenReturn(false);
    when(entry.getReplicatableJournalRecord()).thenReturn(journalRecord(5, 2));
    when(reader.next()).thenReturn(entry);

    // when
    final var next = source.next();

    // then
    assertThat(next.archiveOwn()).isTrue();
    assertThat(next.frame().asqn()).isEqualTo(-1);
  }

  private static IndexedRaftLogEntry applicationEntry(final ValueType... valueTypes) {
    final List<LogAppendEntry> records =
        Arrays.stream(valueTypes)
            .map(
                valueType ->
                    LogAppendEntry.of(
                        new RecordMetadata()
                            .recordType(RecordType.COMMAND)
                            .valueType(valueType)
                            .intent(LogArchiveIntent.ARCHIVE_LOG),
                        new LogArchiveRecord().setTimelineId("tl-0")))
            .toList();
    final long firstPosition = 100;
    final var data =
        SequencedBatchSerializer.serializeBatch(
            new SequencedBatch(1L, firstPosition, -1L, records));
    final var entry = mock(IndexedRaftLogEntry.class);
    when(entry.index()).thenReturn(7L);
    when(entry.term()).thenReturn(3L);
    when(entry.isApplicationEntry()).thenReturn(true);
    when(entry.getApplicationEntry())
        .thenReturn(
            new SerializedApplicationEntry(
                firstPosition, firstPosition + records.size() - 1, data));
    when(entry.getReplicatableJournalRecord()).thenReturn(journalRecord(7, 3));
    return entry;
  }

  private static ReplicatableJournalRecord journalRecord(final long index, final long term) {
    return new ReplicatableJournalRecord(term, index, 42L, new byte[] {1, 2, 3});
  }
}
