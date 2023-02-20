/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.CorruptedJournalException;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentsManagerTest {
  private static final String JOURNAL_NAME = "journal";
  private final TestJournalFactory journalFactory = new TestJournalFactory();

  private @TempDir Path directory;
  private SegmentsManager segments;

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(segments);
  }

  @Test
  void shouldDeleteFilesMarkedForDeletionsOnLoad() {
    // given
    segments = journalFactory.segmentsManager(directory);
    segments.open(0);
    Objects.requireNonNull(segments.getFirstSegment()).createReader();
    segments.getFirstSegment().delete();

    // when
    // opening another journal instance without closing the original one, the segment marked
    // for deletion is deleted. We can't close the first journal instance before because closing
    // will cause the segment to be deleted on close where we actually want to test that the file is
    // deleted when opening.

    try (final var newSegments = journalFactory.segmentsManager(directory)) {
      newSegments.open(0);
      // then
      final File logDirectory = directory.resolve("data").toFile();
      assertThat(logDirectory)
          .isDirectoryNotContaining(
              file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
          .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
    }
  }

  @Test
  void shouldDetectCorruptionAtDescriptorWithAckedEntries() throws Exception {
    // given
    final long index;
    try (final var journal = openJournal()) {
      index = journal.append(journalFactory.entry()).index();
    }

    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith(".log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    segments = journalFactory.segmentsManager(directory);
    assertThatThrownBy(() -> segments.open(index)).isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldNotThrowExceptionWhenCorruptionAtNotAckEntries() throws Exception {
    // given
    final long index;
    try (final var journal = openJournal()) {
      index = journal.append(journalFactory.entry()).index();
      journal.append(journalFactory.entry());
    }

    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith("2.log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatNoException().isThrownBy(() -> segments.open(index));
    assertThat(segments.getFirstSegment())
        .extracting(Segment::index, Segment::lastIndex)
        .containsExactly(index, index);
  }

  @Test
  void shouldNotThrowExceptionWhenCorruptionAtDescriptorWithoutAckedEntries() throws Exception {
    // given
    final var journal = openJournal();
    journal.close();
    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith(".log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatNoException().isThrownBy(() -> segments.open(0));
    assertThat(segments.getFirstSegment())
        .extracting(Segment::index, Segment::lastIndex)
        .containsExactly(1L, 0L);
  }

  @Test
  void shouldDetectMissingEntryAsCorruption() {
    // given
    final var journal = openJournal();
    final var indexInFirstSegment = journal.append(1, journalFactory.entry()).index();
    journal.close();

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatException()
        .isThrownBy(() -> segments.open(indexInFirstSegment + 1))
        .isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldDetectCorruptionInIntermediateSegments() throws Exception {
    // given
    final var journal = openJournal();
    final var indexInFirstSegment = journal.append(1, journalFactory.entry()).index();
    final var lastFlushedIndex = journal.append(2, journalFactory.entry()).index();
    final var firstSegmentFile = journal.getFirstSegment().file().file();
    journal.close();

    LogCorrupter.corruptRecord(firstSegmentFile, indexInFirstSegment);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatException()
        .isThrownBy(() -> segments.open(lastFlushedIndex))
        .isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldNotDetectCorruptionWithUnflushedIndexInIntermediateSegments() throws Exception {
    // given
    final var journal = openJournal();
    final var indexInFirstSegment = journal.append(1, journalFactory.entry()).index();
    journal.append(2, journalFactory.entry()).index();
    final var firstSegmentFile = journal.getFirstSegment().file().file();
    journal.close();

    LogCorrupter.corruptRecord(firstSegmentFile, indexInFirstSegment);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatNoException().isThrownBy(() -> segments.open(0));
  }

  @Test
  void shouldHandlePartiallyWrittenDescriptor() throws Exception {
    // given
    final File dataFile = directory.resolve("data").toFile();
    assertThat(dataFile.mkdirs()).isTrue();
    final File emptyLog = new File(dataFile, "journal-1.log");
    assertThat(emptyLog.createNewFile()).isTrue();

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatNoException().isThrownBy(() -> segments.open(0));
    assertThat(segments.getFirstSegment())
        .extracting(Segment::index, Segment::lastIndex)
        .containsExactly(1L, 0L);
  }

  private SegmentedJournal openJournal() {
    return journalFactory.journal(journalFactory.segmentsManager(directory));
  }
}
