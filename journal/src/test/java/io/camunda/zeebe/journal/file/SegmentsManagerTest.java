/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.agrona.CloseHelper;
import org.agrona.collections.ArrayUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class SegmentsManagerTest {
  private static final String JOURNAL_NAME = "journal";
  @TempDir Path directory;
  private final TestJournalFactory journalFactory = new TestJournalFactory();
  private SegmentsManager segments;

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(segments);
  }

  @Test
  void shouldDeleteFilesMarkedForDeletionsOnLoad() {
    // given
    segments = journalFactory.segmentsManager(directory);
    segments.open();
    Objects.requireNonNull(segments.getFirstSegment()).createReader();
    segments.getFirstSegment().delete();

    // when
    // if we close the current journal, it will delete the files on closing. So we cannot test this
    // scenario.
    try (final var newSegments = journalFactory.segmentsManager(directory)) {
      newSegments.open();
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
    try (final var journal = openJournal()) {
      journal.append(journalFactory.entryData()).index();
    }

    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith(".log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    segments = journalFactory.segmentsManager(directory);
    assertThatThrownBy(() -> segments.open()).isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldNotThrowExceptionWhenCorruptionAtNotAckEntries() throws Exception {
    // given
    final long index;
    try (final var journal = openJournal()) {
      index = journal.append(journalFactory.entryData()).index();
      journal.append(journalFactory.entryData());
    }
    journalFactory.metaStore().storeLastFlushedIndex(index);

    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith("2.log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatNoException().isThrownBy(() -> segments.open());
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
    assertThatNoException().isThrownBy(() -> segments.open());
    assertThat(segments.getFirstSegment())
        .extracting(Segment::index, Segment::lastIndex)
        .containsExactly(1L, 0L);
  }

  @Test
  void shouldDetectMissingEntryAsCorruption() {
    // given
    final var journal = openJournal();
    final var indexInFirstSegment = journal.append(1, journalFactory.entryData()).index();
    journal.close();
    journalFactory.metaStore().storeLastFlushedIndex(indexInFirstSegment + 1);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatCode(segments::open).isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldDetectCorruptionInIntermediateSegments() throws Exception {
    // given
    final var journal = openJournal();
    final var indexInFirstSegment = journal.append(1, journalFactory.entryData()).index();
    final var lastFlushedIndex = journal.append(2, journalFactory.entryData()).index();
    final var firstSegmentFile = journal.getFirstSegment().file().file();
    journal.close();

    LogCorrupter.corruptRecord(firstSegmentFile, indexInFirstSegment);

    // when
    segments = journalFactory.segmentsManager(directory);
    journalFactory.metaStore().storeLastFlushedIndex(lastFlushedIndex);

    // then
    assertThatCode(segments::open).isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldNotDetectCorruptionWithUnflushedIndexInIntermediateSegments() throws Exception {
    // given
    final var journal = openJournal();
    final var indexInFirstSegment = journal.append(1, journalFactory.entryData()).index();
    journal.append(2, journalFactory.entryData()).index();
    final var firstSegmentFile = journal.getFirstSegment().file().file();
    journal.close();
    journalFactory.metaStore().storeLastFlushedIndex(0);

    LogCorrupter.corruptRecord(firstSegmentFile, indexInFirstSegment);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatNoException().isThrownBy(segments::open);
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
    assertThatNoException().isThrownBy(() -> segments.open());
    assertThat(segments.getFirstSegment())
        .extracting(Segment::index, Segment::lastIndex)
        .containsExactly(1L, 0L);
  }

  @Test
  void shouldHandleCrashOnResetAfterDeletionBeforeSegmentIsCreated() throws IOException {
    // given
    try (final var journal = openJournal()) {
      journal.append(1, journalFactory.entryData()).index();
      journal.append(2, journalFactory.entryData()).index();
      journal.append(3, journalFactory.entryData()).index();
      journal.reset(10);

      // when - simulate "failure" by corrupting the descriptor
      // we cannot close the journal here as we want to avoid flushing. instead, open a separate
      // instance of the SegmentsManager
      segments = journalFactory.segmentsManager(directory);
      segments.open();
      LogCorrupter.corruptDescriptor(
          Objects.requireNonNull(segments.getFirstSegment()).file().file());
      segments.close();

      // then
      assertThatNoException().isThrownBy(() -> segments.open());
    }
  }

  @Test
  void shouldHandleCrashOnTruncateAfterDeletionBeforeSegmentIsCreated() {
    // given
    try (final var journal = openJournal()) {
      journal.append(1, journalFactory.entryData()).index();
      final var index = journal.append(2, journalFactory.entryData()).index();
      journal.append(3, journalFactory.entryData()).index();
      journal.deleteAfter(index);
    }

    // when - simulate crash after creating the next segment but before writing or flushing anything
    final var expectedRootCause = new IOException("failed");
    Exception invalidSegmentWasCreated = null;
    try (final var failingSegments =
        journalFactory.segmentsManager(
            directory,
            new SegmentLoader(
                (channel, segmentSize) -> {
                  SegmentAllocator.fill().allocate(channel, segmentSize);
                  throw expectedRootCause;
                }))) {
      failingSegments.open();
      // will allocate the next segment
      failingSegments.getNextSegment();
    } catch (final Exception error) {
      invalidSegmentWasCreated = error;
    }

    // then
    assertThat(invalidSegmentWasCreated).hasRootCause(expectedRootCause);
    segments = journalFactory.segmentsManager(directory);
    assertThatNoException().isThrownBy(() -> segments.open());
  }

  @RegressionTest("https://github.com/camunda/zeebe/issues/12754")
  void shouldDeleteSegmentsInReverseOrderOnReset() {
    // given
    final var loader = Mockito.spy(journalFactory.segmentLoader());
    final var metaStore = Mockito.spy(journalFactory.metaStore());
    try (final var journal = openJournal()) {
      journal.append(1, journalFactory.entry()).index();
      journal.append(2, journalFactory.entry()).index();
      journal.append(3, journalFactory.entry()).index();
    }

    // spy on all created segments, so we can assert the order in which they're deleted
    //noinspection resource
    Mockito.doAnswer(call -> Mockito.spy(call.callRealMethod()))
        .when(loader)
        .loadExistingSegment(Mockito.any(), Mockito.anyLong(), Mockito.any());

    // when
    segments = journalFactory.segmentsManager(directory, loader, metaStore);
    try (final var journal = journalFactory.journal(segments)) {
      // grab all segments and copy them to avoid the map getting cleared
      final var loadedSegments = getJournalSegments(segments);
      journal.reset(10);

      // then - assert we reset first, then delete the segments in reversed order
      final var mocks = ArrayUtil.add(loadedSegments.toArray(), metaStore);
      final var inOrder = Mockito.inOrder(mocks);

      inOrder.verify(metaStore).resetLastFlushedIndex();
      loadedSegments.forEach(s -> inOrder.verify(s).delete());
      inOrder.verifyNoMoreInteractions();
    }
  }

  private List<Segment> getJournalSegments(final SegmentsManager segmentsManager) {
    final var segments = new ArrayList<Segment>();
    final long firstIndex =
        Optional.ofNullable(segmentsManager.getFirstSegment()).map(Segment::index).orElse(0L);
    var segment = segmentsManager.getLastSegment();
    long nextSegmentIndex = (segment == null ? -1 : segment.index() - 1);

    while (segment != null && nextSegmentIndex > firstIndex) {
      segments.add(segment);
      nextSegmentIndex = segment.index() - 1;
      segment = segmentsManager.getSegment(nextSegmentIndex);
    }

    return segments;
  }

  private SegmentedJournal openJournal() {
    return journalFactory.journal(directory);
  }
}
