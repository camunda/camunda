/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal;

import static io.camunda.zeebe.journal.file.SegmentedJournal.ASQN_IGNORE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JournalReaderTest {

  private static final int ENTRIES = 4;
  @TempDir File directory;

  private final UnsafeBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final BufferWriter recordDataWriter = new DirectBufferWriter().wrap(data);

  @Test
  void shouldSeek() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, recordDataWriter).index();
    }

    // when
    final long nextIndex = reader.seek(2);

    // then
    for (int i = 0; i < 3; i++) {
      final JournalRecord record = reader.next();
      assertThat(record.asqn()).isEqualTo(nextIndex + i);
      assertThat(record.index()).isEqualTo(nextIndex + i);
      assertThat(record.data()).isEqualTo(data);
    }
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldSeekToFirst() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, recordDataWriter).index();
    }
    reader.next(); // move reader before seekToFirst
    reader.next();

    // when
    final long nextIndex = reader.seekToFirst();

    // then
    assertThat(nextIndex).isEqualTo(journal.getFirstIndex());
    final JournalRecord record = reader.next();
    assertThat(record.index()).isEqualTo(nextIndex);
    assertThat(record.asqn()).isEqualTo(1);

    journal.close();
  }

  @Test
  void shouldSeekToLast() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, recordDataWriter).index();
    }

    // when
    final long nextIndex = reader.seekToLast();

    // then
    assertThat(nextIndex).isEqualTo(journal.getLastIndex());
    final JournalRecord record = reader.next();
    assertThat(record.index()).isEqualTo(nextIndex);
    assertThat(record.asqn()).isEqualTo(4);
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldNotReadIfSeekIsHigherThanLast() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, recordDataWriter).index();
    }

    // when
    final long nextIndex = reader.seek(99L);

    // then
    assertThat(nextIndex).isEqualTo(journal.getLastIndex() + 1);
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldReadAppendedDataAfterSeek() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    for (int i = 0; i < ENTRIES; i++) {
      journal.append(recordDataWriter).index();
    }

    // when
    final long nextIndex = reader.seek(99L);
    assertThat(reader.hasNext()).isFalse();
    journal.append(recordDataWriter);

    // then
    assertThat(nextIndex).isEqualTo(journal.getLastIndex());
    assertThat(reader.hasNext()).isTrue();

    journal.close();
  }

  @Test
  void shouldSeekToAsqn() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    // given that all records with index i will have an asqn of startAsqn + i
    final long startAsqn = 10;
    for (int i = 0; i < ENTRIES; i++) {
      assertThat(journal.append(startAsqn + i, recordDataWriter)).isNotNull();
    }
    assertThat(reader.hasNext()).isTrue();

    // when
    final long nextIndex = reader.seekToAsqn(startAsqn + 2);

    // then
    assertThat(nextIndex).isEqualTo(3);
    assertThat(reader.hasNext()).isTrue();

    final JournalRecord next = reader.next();
    assertThat(next.index()).isEqualTo(nextIndex);
    assertThat(next.asqn()).isEqualTo(startAsqn + 2);

    journal.close();
  }

  @Test
  void shouldSeekToHighestAsqnLowerThanProvidedAsqn() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    final var expectedRecord = journal.append(1, recordDataWriter);
    journal.append(5, recordDataWriter);

    // when
    final long nextIndex = reader.seekToAsqn(4);

    // then
    assertThat(nextIndex).isEqualTo(expectedRecord.index());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(expectedRecord);

    journal.close();
  }

  @Test
  void shouldSeekToHighestAsqnWithinBoundIndex() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    final var firstIndex = journal.append(1, recordDataWriter).index();
    final var secondIndex = journal.append(4, recordDataWriter).index();
    final var thirdIndex = journal.append(recordDataWriter).index();
    final var fourthIndex = journal.append(5, recordDataWriter).index();
    journal.append(recordDataWriter).index();

    // when - then
    assertThat(reader.seekToAsqn(5, firstIndex)).isEqualTo(firstIndex);
    assertThat(reader.next().asqn()).isEqualTo(1);

    assertThat(reader.seekToAsqn(5, secondIndex)).isEqualTo(secondIndex);
    assertThat(reader.next().asqn()).isEqualTo(4);

    assertThat(reader.seekToAsqn(5, thirdIndex)).isEqualTo(secondIndex);
    assertThat(reader.next().asqn()).isEqualTo(4);

    assertThat(reader.seekToAsqn(5, fourthIndex)).isEqualTo(fourthIndex);
    assertThat(reader.next().asqn()).isEqualTo(5);

    assertThat(reader.seekToAsqn(Long.MAX_VALUE)).isEqualTo(fourthIndex);
    assertThat(reader.next().asqn()).isEqualTo(5);

    journal.close();
  }

  @Test
  void shouldSeekToLastAsqn() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    final var expectedRecord = journal.append(5, recordDataWriter);
    journal.append(recordDataWriter);

    // when - then
    assertThat(reader.seekToAsqn(Long.MAX_VALUE)).isEqualTo(expectedRecord.index());
    assertThat(reader.next()).isEqualTo(expectedRecord);

    journal.close();
  }

  @Test
  void shouldSeekToHighestLowerAsqnSkippingRecordsWithNoAsqn() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    final var expectedRecord = journal.append(1, recordDataWriter);
    journal.append(recordDataWriter);
    journal.append(5, recordDataWriter);

    // when
    final long nextIndex = reader.seekToAsqn(3);

    // then
    assertThat(nextIndex).isEqualTo(expectedRecord.index());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(expectedRecord);

    journal.close();
  }

  @Test
  void shouldSeekToFirstWhenAllAsqnIsHigher() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    final var expectedRecord = journal.append(recordDataWriter);
    journal.append(5, recordDataWriter);

    // when
    final long nextIndex = reader.seekToAsqn(1);

    // then
    assertThat(nextIndex).isEqualTo(expectedRecord.index());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(expectedRecord);
    assertThat(reader.next().asqn()).isEqualTo(5);

    journal.close();
  }

  @Test
  void shouldSeekToFirstIfLowerThanFirst() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, recordDataWriter).index();
    }

    // when
    final long nextIndex = reader.seek(-1);

    // then
    assertThat(nextIndex).isEqualTo(journal.getFirstIndex());
    assertThat(reader.hasNext()).isTrue();
    final JournalRecord record = reader.next();
    assertThat(record.asqn()).isEqualTo(1);
    assertThat(record.index()).isEqualTo(nextIndex);
    assertThat(record.data()).isEqualTo(data);

    journal.close();
  }

  @Test
  void shouldSeekAfterTruncate() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    long lastIndex = -1;
    for (int i = 1; i <= ENTRIES; i++) {
      lastIndex = journal.append(i, recordDataWriter).index();
    }

    // when
    journal.deleteAfter(lastIndex - 2);
    final long nextIndex = reader.seek(lastIndex - 2);

    // then
    assertThat(nextIndex).isEqualTo(lastIndex - 2);
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(nextIndex);
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldSeekAfterCompact() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    journal.append(1, recordDataWriter).index();
    journal.append(2, recordDataWriter).index();
    journal.append(3, recordDataWriter).index();

    // when
    journal.deleteUntil(3);
    final long nextIndex = reader.seek(3);

    // then
    assertThat(nextIndex).isEqualTo(3);
    final JournalRecord next = reader.next();
    assertThat(next.index()).isEqualTo(nextIndex);
    assertThat(next.asqn()).isEqualTo(3);
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldSeekToIndex() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    long asqn = 1;
    JournalRecord lastRecordWritten = null;
    for (int i = 1; i <= ENTRIES; i++) {
      final JournalRecord record = journal.append(asqn++, recordDataWriter);
      assertThat(record.index()).isEqualTo(i);
      lastRecordWritten = record;
    }
    assertThat(reader.hasNext()).isTrue();

    // when - compact up to the first index of segment 3
    final long nextIndex = reader.seek(lastRecordWritten.index() - 1);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(nextIndex);

    journal.close();
  }

  @Test
  void shouldSeekToFirstWithEmptyJournal() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    // when
    final long nextIndex = reader.seekToFirst();

    // then
    assertThat(nextIndex).isEqualTo(journal.getFirstIndex());
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldSeekToLastWithEmptyJournal() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    // when
    final long nextIndex = reader.seekToLast();

    // then
    assertThat(nextIndex).isEqualTo(journal.getLastIndex());
    assertThat(reader.hasNext()).isFalse();

    journal.close();
  }

  @Test
  void shouldSeekToFirstWhenNoRecordsWithValidAsqnExists() {
    // given
    final var journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    final var reader = journal.openReader();

    for (int i = 0; i < ENTRIES; i++) {
      journal.append(recordDataWriter);
    }

    // when
    final long nextIndex = reader.seekToAsqn(32);

    // then
    assertThat(nextIndex).isEqualTo(journal.getFirstIndex());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().asqn()).isEqualTo(ASQN_IGNORE);

    journal.close();
  }
}
