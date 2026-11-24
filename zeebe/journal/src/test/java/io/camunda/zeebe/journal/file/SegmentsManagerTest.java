/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.CloseHelper;
import org.agrona.collections.ArrayUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

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
  void shouldKeepSegmentOpenWhenDeletingItFails() throws IOException {
    // given - a segment whose file cannot be renamed for deletion, because it is gone
    segments = journalFactory.segmentsManager(directory);
    segments.open();
    final var segment = Objects.requireNonNull(segments.getFirstSegment());
    Files.delete(segment.file().file());

    // when
    assertThatThrownBy(segment::delete).isInstanceOf(JournalException.class);

    // then - nothing was deleted, so readers can still use it
    assertThat(segment.isOpen()).isTrue();
  }

  @Test
  void shouldDeleteFilesMarkedForDeletionsOnLoad() {
    // given
    segments = journalFactory.segmentsManager(directory);
    segments.open();
    Objects.requireNonNull(segments.getFirstSegment()).createReader();
    segments.getFirstSegment().delete();

    // when
    // opening another journal instance without closing the original one, the segment marked
    // for deletion is deleted. We can't close the first journal instance before because closing
    // will cause the segment to be deleted on close where we actually want to test that the file is
    // deleted when opening.

    try (final var newSegments = journalFactory.segmentsManager(directory)) {
      newSegments.open();
      // then
      final Path logDirectory = directory.resolve("data");
      assertThat(logDirectory)
          .isDirectoryNotContaining(
              p -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, p.getFileName().toString()))
          .isDirectoryContaining(p -> SegmentFile.isSegmentFile(JOURNAL_NAME, p));
    }
  }

  @Test
  void shouldDetectCorruptionAtDescriptorWithAckedEntries() throws Exception {
    // given
    try (final var journal = openJournal()) {
      journal.append(journalFactory.entry()).index();
    }

    final Path dataFile = directory.resolve("data");
    final Path logFile;
    try (final var stream = Files.list(dataFile)) {
      logFile =
          stream.filter(p -> p.getFileName().toString().endsWith(".log")).findFirst().orElseThrow();
    }
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
      index = journal.append(journalFactory.entry()).index();
      journal.append(journalFactory.entry());
    }
    journalFactory.metaStore().storeLastFlushedIndex(index);

    final Path dataFile = directory.resolve("data");
    final Path logFile;
    try (final var stream = Files.list(dataFile)) {
      logFile =
          stream
              .filter(p -> p.getFileName().toString().endsWith("2.log"))
              .findFirst()
              .orElseThrow();
    }
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
    final Path dataFile = directory.resolve("data");
    final Path logFile;
    try (final var stream = Files.list(dataFile)) {
      logFile =
          stream.filter(p -> p.getFileName().toString().endsWith(".log")).findFirst().orElseThrow();
    }
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
    final var indexInFirstSegment = journal.append(1, journalFactory.entry()).index();
    journal.close();
    journalFactory.metaStore().storeLastFlushedIndex(indexInFirstSegment + 1);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatException()
        .isThrownBy(() -> segments.open())
        .isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldDetectCorruptionInIntermediateSegments() throws Exception {
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
    assertThatException()
        .isThrownBy(() -> segments.open())
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
    journalFactory.metaStore().storeLastFlushedIndex(0);

    LogCorrupter.corruptRecord(firstSegmentFile, indexInFirstSegment);

    // when
    segments = journalFactory.segmentsManager(directory);

    // then
    assertThatNoException().isThrownBy(() -> segments.open());
  }

  @Test
  void shouldHandlePartiallyWrittenDescriptor() throws Exception {
    // given
    final Path dataFile = directory.resolve("data");
    Files.createDirectories(dataFile);
    Files.createFile(dataFile.resolve("journal-1.log"));

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
      journal.append(1, journalFactory.entry()).index();
      journal.append(2, journalFactory.entry()).index();
      journal.append(3, journalFactory.entry()).index();
      journal.reset(10);
    }

    // when - simulate "failure" by corrupting the descriptor
    segments = journalFactory.segmentsManager(directory);
    segments.open();
    LogCorrupter.corruptDescriptor(
        Objects.requireNonNull(segments.getFirstSegment()).file().file());
    segments.close();

    // then
    assertThatNoException().isThrownBy(() -> segments.open());
  }

  @Test
  void shouldHandleCrashOnTruncateAfterDeletionBeforeSegmentIsCreated() {
    // given
    try (final var journal = openJournal()) {
      journal.append(1, journalFactory.entry()).index();
      final var index = journal.append(2, journalFactory.entry()).index();
      journal.append(3, journalFactory.entry()).index();
      journal.deleteAfter(index);
    }

    // when - simulate crash after creating the next segment but before writing or flushing anything
    final var expectedRootCause = new IOException("failed");
    Exception invalidSegmentWasCreated = null;
    try (final var failingSegments =
        journalFactory.segmentsManager(
            directory,
            new SegmentLoader(
                Long.MIN_VALUE,
                journalFactory.metrics(),
                (channel, fd, segmentSize) -> {
                  SegmentAllocator.posixOrFill().allocate(channel, fd, segmentSize);
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

  @RegressionTest("https://github.com/camunda/camunda/issues/12754")
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
    try (final var journal = journalFactory.journal(segments, metaStore)) {
      // grab all segments and copy them to avoid the map getting cleared
      final var loadedSegments = new ArrayList<>(segments.getTailSegments(0).values());
      journal.reset(10);

      // then - assert we reset first, then delete the segments in reversed order
      final var mocks = ArrayUtil.add(loadedSegments.toArray(), metaStore);
      final var inOrder = Mockito.inOrder(mocks);

      inOrder.verify(metaStore).resetLastFlushedIndex();

      Collections.reverse(loadedSegments);
      loadedSegments.forEach(s -> inOrder.verify(s).delete());
      inOrder.verifyNoMoreInteractions();
    }
  }

  @Test
  void shouldForceNewFirstSegmentBeforeDeletingOldSegmentsOnReset() {
    // given - a journal whose segments are spied on, including the one the reset creates
    final var loader = Mockito.spy(journalFactory.segmentLoader());
    try (final var journal = openJournal()) {
      journal.append(1, journalFactory.entry()).index();
      journal.append(2, journalFactory.entry()).index();
    }
    //noinspection resource
    Mockito.doAnswer(call -> Mockito.spy(call.callRealMethod()))
        .when(loader)
        .loadExistingSegment(Mockito.any(), Mockito.anyLong(), Mockito.any());
    //noinspection resource
    Mockito.doAnswer(call -> Mockito.spy(call.callRealMethod()))
        .when(loader)
        .createSegment(Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.any());
    segments = journalFactory.segmentsManager(directory, loader);
    try (final var journal = journalFactory.journal(segments)) {
      final var oldLastSegment = segments.getLastSegment();

      // when
      journal.reset(10);

      // then - the new first segment is on disk before any old segment is gone, so that a power
      // loss after the deletion still finds the reset
      final var newFirstSegment = segments.getFirstSegment();
      final var inOrder = Mockito.inOrder(newFirstSegment, oldLastSegment);
      inOrder.verify(newFirstSegment).flushDescriptor();
      inOrder.verify(oldLastSegment).delete();
    }
  }

  @Test
  void shouldCloseNewFirstSegmentWhenDiscardingItAfterFailedResetFails() {
    // given - deleting any segment fails, the old ones and the one the reset creates
    final var loader = Mockito.spy(journalFactory.segmentLoader());
    try (final var journal = openJournal()) {
      journal.append(1, journalFactory.entry()).index();
      journal.append(2, journalFactory.entry()).index();
    }
    final var deleteFailure = new JournalException("Injected: failed to delete the old segment");
    final var discardFailure = new JournalException("Injected: failed to delete the new segment");
    final var newSegment = new AtomicReference<Segment>();
    //noinspection resource
    Mockito.doAnswer(
            call -> {
              final var spy = Mockito.spy((Segment) call.callRealMethod());
              Mockito.doThrow(deleteFailure).when(spy).delete();
              return spy;
            })
        .when(loader)
        .loadExistingSegment(Mockito.any(), Mockito.anyLong(), Mockito.any());
    //noinspection resource
    Mockito.doAnswer(
            call -> {
              final var spy = Mockito.spy((Segment) call.callRealMethod());
              Mockito.doThrow(discardFailure).when(spy).delete();
              newSegment.set(spy);
              return spy;
            })
        .when(loader)
        .createSegment(Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.any());
    segments = journalFactory.segmentsManager(directory, loader);
    try (final var journal = journalFactory.journal(segments)) {

      // when
      final var error = catchThrowable(() -> journal.reset(10));

      // then - the reset fails for the old segment, and the new one is closed, not left mapped
      assertThat(error).isSameAs(deleteFailure).hasSuppressedException(discardFailure);
      assertThat(newSegment.get().isOpen()).isFalse();
    }
  }

  @Test
  void shouldPrepareNextSegmentOnBgIoThread() {
    // given - the next segment is prepared asynchronously; this does blocking I/O, so it must not
    // occupy a thread of the JVM-wide common ForkJoinPool, where it could starve unrelated work
    final var loader = Mockito.spy(journalFactory.segmentLoader());
    final var preparingThread = new AtomicReference<Thread>();
    Mockito.doAnswer(
            call -> {
              preparingThread.set(Thread.currentThread());
              return call.callRealMethod();
            })
        .when(loader)
        .createUninitializedSegment(Mockito.any(), Mockito.any(), Mockito.any());
    segments =
        journalFactory.segmentsManager(
            directory, loader, journalFactory.metaStore(), "bg-io-probe");
    segments.open();

    // when - rolling over triggers preparing the following segment, which is awaited on the next
    segments.getNextSegment();
    segments.getNextSegment();

    // then - the prep runs on the journal's dedicated background I/O platform thread; the thread
    // name is the identity the executor exposes, and is what operators see in thread dumps
    assertThat(preparingThread.get())
        .isNotNull()
        .extracting(Thread::getName)
        .isEqualTo("journal-bg-io-bg-io-probe");
  }

  @Test
  void shouldCloseBackgroundIoExecutor() throws InterruptedException {
    // given - capture the bg I/O thread via the prep task it runs
    final var loader = Mockito.spy(journalFactory.segmentLoader());
    final var preparingThread = new AtomicReference<Thread>();
    Mockito.doAnswer(
            call -> {
              preparingThread.set(Thread.currentThread());
              return call.callRealMethod();
            })
        .when(loader)
        .createUninitializedSegment(Mockito.any(), Mockito.any(), Mockito.any());
    segments = journalFactory.segmentsManager(directory, loader);
    segments.open();
    segments.getNextSegment();
    segments.getNextSegment();

    // when
    segments.close();

    // then
    final var bgIoThread = preparingThread.get();
    assertThat(bgIoThread.join(Duration.ofSeconds(10))).as("bg I/O thread is terminated").isTrue();
  }

  @Test
  void shouldRollOverSegmentAfterReopening() {
    // given
    segments = journalFactory.segmentsManager(directory);
    segments.open();
    segments.close();
    segments.open();

    // when
    final var first = segments.getNextSegment();
    final var second = segments.getNextSegment();

    // then
    assertThat(second.id()).isGreaterThan(first.id());
  }

  private SegmentedJournal openJournal() {
    return journalFactory.journal(journalFactory.segmentsManager(directory));
  }
}
