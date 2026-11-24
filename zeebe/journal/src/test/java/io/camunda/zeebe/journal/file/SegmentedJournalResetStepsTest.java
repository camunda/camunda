/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalException.OutOfDiskSpace;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

/**
 * Resets the journal, as a follower does before it commits a received snapshot, and crashes or
 * fails it at every step that changes what is on disk: storing or resetting the last flushed index,
 * allocating a new segment and deleting each old segment.
 *
 * <p>A crash is simulated with a {@link JournalCrash} taken right before and right after each step,
 * from which a new journal is opened. A failure is simulated by letting the step fail with the
 * error it fails with in production, after which the journal keeps being used, as Raft does after a
 * failed snapshot install: it appends and restarts, or retries the reset.
 *
 * <p>Either way, the journal must end up with the entries from before the reset, or a prefix of
 * them (old segments are deleted newest first, so that no gap opens in the log), or reset.
 *
 * <p>Steps are found with spies and hooks, not with a seam for each file operation, so a crash
 * between two file operations of one step is only covered where a hook sits between them.
 */
final class SegmentedJournalResetStepsTest {

  private static final int ENTRIES_PER_SEGMENT = 2;
  private static final long RESET_INDEX = 101;
  private static final BufferWriter ENTRY = new DirectBufferWriter(BufferUtil.wrapString("test"));

  private @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> shouldRecoverFromCrashAtAnyStepOfReset() throws FlushException {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final var preCreated : PreCreatedSegment.values()) {
      for (final boolean readerHoldsSegment : new boolean[] {false, true}) {
        final var config = new Config(preCreated, readerHoldsSegment);
        final var crashes = crashAtEveryStep(config);
        for (final var crash : crashes.crashes()) {
          tests.add(
              dynamicTest(
                  config + ", crash " + crash.description(),
                  () -> assertRecoversFromCrash(crash.crash(), crashes.oldEntries())));
        }
      }
    }
    return tests.stream();
  }

  @TestFactory
  Stream<DynamicTest> shouldKeepJournalUsableWhenAnyStepOfResetFails() throws FlushException {
    final List<DynamicTest> tests = new ArrayList<>();
    for (final var preCreated : PreCreatedSegment.values()) {
      // a reader keeps the old last segment mapped: if the bug comes back, appending into it after
      // it was deleted then fails the test instead of crashing the test JVM
      final var config = new Config(preCreated, true);
      final var steps = stepsOfSuccessfulReset(config);
      for (int failAt = 0; failAt < steps.size(); failAt++) {
        final int step = failAt;
        final String failure = config + ", fail " + steps.get(step);
        tests.add(
            dynamicTest(
                failure + ", then append and restart",
                () -> failThenAppend(config, step, steps.get(step))));
        tests.add(
            dynamicTest(failure + ", then retry the reset", () -> failThenRetry(config, step)));
      }
    }
    return tests.stream();
  }

  private Crashes crashAtEveryStep(final Config config) throws FlushException {
    final var root = newDirectory("crash-" + config.fileName());
    final var crashes = new ArrayList<NamedCrash>();
    final var steps = new ResetSteps(ResetSteps.NO_FAILURE);
    try (final var journal = ResetJournal.open(root, config, steps)) {
      final Consumer<String> crash =
          description -> {
            final var target = root.resolveSibling(root.getFileName() + "-" + crashes.size());
            crashes.add(
                new NamedCrash(description, JournalCrash.capture(root, journal.metaStore, target)));
          };
      steps.onStep(crash);
      steps.during(journal::reset);
      crash.accept("after the reset");
      return new Crashes(crashes, journal.oldEntries);
    }
  }

  private List<String> stepsOfSuccessfulReset(final Config config) throws FlushException {
    final var steps = new ResetSteps(ResetSteps.NO_FAILURE);
    try (final var journal =
        ResetJournal.open(newDirectory("dry-run-" + config.fileName()), config, steps)) {
      steps.during(journal::reset);
      return steps.names();
    }
  }

  private void assertRecoversFromCrash(
      final JournalCrash crash, final List<JournalEntry> oldEntries) throws FlushException {
    // when - the node restarts from what the crash left on disk
    final List<JournalEntry> expected;
    final JournalMetaStore metaStore;
    try (final var restarted = Restarted.open(crash.root(), crash.metaStore())) {
      // then - the journal holds the old log or a prefix of it, or the reset log
      assertOldPrefixOrReset(restarted.journal(), oldEntries);

      // and it appends, flushes and opens again with what it appended
      expected = new ArrayList<>(JournalEntry.readAll(restarted.journal()));
      expected.add(JournalEntry.of(restarted.journal().append(ENTRY)));
      restarted.journal().flush();
      metaStore = restarted.metaStore();
    }
    try (final var reopened = Restarted.open(crash.root(), metaStore)) {
      assertThat(JournalEntry.readAll(reopened.journal())).containsExactlyElementsOf(expected);
    }
  }

  private void failThenAppend(final Config config, final int failAt, final String failedStep)
      throws FlushException {
    // given
    final var root = newDirectory("fail-append-" + config.fileName() + "-" + failAt);
    final var steps = new ResetSteps(failAt);
    try (final var journal = ResetJournal.open(root, config, steps)) {
      final var segmentFilesBefore = segmentFiles(root);

      // when - a step of the reset fails
      assertThatThrownBy(() -> steps.during(journal::reset)).isNotNull();

      // then - the journal is still consistent and readable
      assertOldPrefixOrReset(journal.journal, journal.oldEntries);
      final var expected = new ArrayList<>(JournalEntry.readAll(journal.journal));

      // and the next flush stores no index after what is left
      journal.journal.flush();
      assertThat(journal.metaStore.loadLastFlushedIndex()).isEqualTo(expected.getLast().index());

      // and a failed delete leaves no new segment behind
      if (failedStep.startsWith("delete segment")) {
        assertThat(segmentFiles(root)).isSubsetOf(segmentFilesBefore);
      }

      // when - a new leader continues the old log, and the node then restarts gracefully
      final var appended = JournalEntry.of(journal.journal.append(ENTRY));
      assertThat(appended.index()).isEqualTo(expected.getLast().index() + 1);
      expected.add(appended);

      // then - the reader that followed the log continues with the appended entry
      assertThat(journal.reader.hasNext()).isTrue();
      assertThat(JournalEntry.of(journal.reader.next())).isEqualTo(appended);
      journal.closeJournal();

      // then - the appended entry survived the restart, so it was not written to a deleted segment
      try (final var restarted = Restarted.open(root, journal.metaStore)) {
        assertThat(JournalEntry.readAll(restarted.journal())).containsExactlyElementsOf(expected);
      }
    }
  }

  private void failThenRetry(final Config config, final int failAt) throws FlushException {
    // given
    final var root = newDirectory("fail-retry-" + config.fileName() + "-" + failAt);
    final var steps = new ResetSteps(failAt);
    try (final var journal = ResetJournal.open(root, config, steps)) {
      // when - a step of the reset fails, and the leader sends the snapshot again
      assertThatThrownBy(() -> steps.during(journal::reset)).isNotNull();
      journal.reset();

      // then - the retry resets the journal, and it survives a restart
      assertThat(journal.journal.getFirstIndex()).isEqualTo(RESET_INDEX);
      assertThat(journal.journal.getLastIndex()).isEqualTo(RESET_INDEX - 1);
      final var appended = JournalEntry.of(journal.journal.append(ENTRY));
      assertThat(appended.index()).isEqualTo(RESET_INDEX);
      journal.closeJournal();
      try (final var restarted = Restarted.open(root, journal.metaStore)) {
        assertThat(JournalEntry.readAll(restarted.journal())).containsExactly(appended);
      }
    }
  }

  private static void assertOldPrefixOrReset(
      final SegmentedJournal journal, final List<JournalEntry> oldEntries) {
    final var entries = JournalEntry.readAll(journal);
    if (entries.isEmpty()) {
      assertThat(journal.getFirstIndex())
          .describedAs("first index of an empty journal, which must be the reset one")
          .isEqualTo(RESET_INDEX);
      assertThat(journal.getLastIndex()).isEqualTo(RESET_INDEX - 1);
    } else {
      assertThat(entries)
          .describedAs("entries, which must be a prefix of the entries before the reset")
          .hasSizeLessThanOrEqualTo(oldEntries.size())
          .containsExactlyElementsOf(oldEntries.subList(0, entries.size()));
      assertThat(journal.getLastIndex()).isEqualTo(entries.getLast().index());
    }
  }

  private static List<String> segmentFiles(final Path root) {
    try (final var files = Files.list(root.resolve("data"))) {
      return files
          .map(file -> file.getFileName().toString())
          .filter(name -> SegmentFile.isSegmentFile("journal", name))
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path newDirectory(final String name) {
    try {
      return Files.createDirectories(directory.resolve(name));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private enum PreCreatedSegment {
    /** No segment was rolled over since the journal opened, so none is pre-created. */
    NONE,
    /** The next segment was pre-created after the last roll-over. */
    READY,
    /** Pre-creating the next segment failed, e.g. because the disk was already full. */
    FAILED
  }

  private record Config(PreCreatedSegment preCreated, boolean readerHoldsSegment) {
    String fileName() {
      return preCreated.name().toLowerCase() + "-" + (readerHoldsSegment ? "reader" : "no-reader");
    }

    @Override
    public String toString() {
      return "pre-created segment " + preCreated + (readerHoldsSegment ? ", with reader" : "");
    }
  }

  private record NamedCrash(String description, JournalCrash crash) {}

  private record Crashes(List<NamedCrash> crashes, List<JournalEntry> oldEntries) {}

  /** A journal opened again, as a restarted node does, with a copy of the last flushed index. */
  private record Restarted(SegmentedJournal journal, JournalMetaStore metaStore)
      implements AutoCloseable {

    static Restarted open(final Path root, final JournalMetaStore lastFlushedIndex) {
      final var factory = new TestJournalFactory("test", ENTRIES_PER_SEGMENT);
      JournalCrash.copyLastFlushedIndex(lastFlushedIndex, factory.metaStore());
      return new Restarted(factory.journal(factory.segmentsManager(root)), factory.metaStore());
    }

    @Override
    public void close() {
      journal.close();
    }
  }

  /**
   * A flushed journal of eight entries in four segments, or of nine entries in five segments after
   * a roll-over that pre-creates the next segment, whose reset steps are recorded. Its segments are
   * spied on, so that deleting them is a step.
   */
  private static final class ResetJournal implements AutoCloseable {
    private final StepMetaStore metaStore;
    private final SegmentedJournal journal;
    private final JournalReader reader;
    private final List<JournalEntry> oldEntries;
    private boolean closed;

    private ResetJournal(
        final StepMetaStore metaStore,
        final SegmentedJournal journal,
        final JournalReader reader,
        final List<JournalEntry> oldEntries) {
      this.metaStore = metaStore;
      this.journal = journal;
      this.reader = reader;
      this.oldEntries = oldEntries;
    }

    static ResetJournal open(final Path root, final Config config, final ResetSteps steps)
        throws FlushException {
      final var factory = new TestJournalFactory("test", ENTRIES_PER_SEGMENT, allocator(steps));
      final var metaStore = new StepMetaStore(steps);

      // entries 1-7, so that the segments are loaded, and spied on, when the journal is reopened
      try (final var journal =
          factory.journal(
              factory.directSegmentsManager(root, factory.segmentLoader(), metaStore), metaStore)) {
        for (int i = 0; i < 7; i++) {
          journal.append(ENTRY);
        }
        journal.flush();
      }

      final var loader = Mockito.spy(factory.segmentLoader());
      Mockito.doAnswer(call -> spySegment(call, steps))
          .when(loader)
          .loadExistingSegment(Mockito.any(), Mockito.anyLong(), Mockito.any());
      Mockito.doAnswer(call -> spySegment(call, steps))
          .when(loader)
          .createSegment(Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.any());
      if (config.preCreated() == PreCreatedSegment.FAILED) {
        Mockito.doThrow(new OutOfDiskSpace("Injected: no space to pre-create the next segment"))
            .when(loader)
            .createUninitializedSegment(Mockito.any(), Mockito.any(), Mockito.any());
      }
      final var journal =
          factory.journal(factory.directSegmentsManager(root, loader, metaStore), metaStore);

      // entry 8 fills the last segment; entry 9 rolls over, which pre-creates the next segment
      journal.append(ENTRY);
      if (config.preCreated() != PreCreatedSegment.NONE) {
        journal.append(ENTRY);
      }
      journal.flush();

      JournalReader reader = null;
      if (config.readerHoldsSegment()) {
        reader = journal.openReader();
        while (reader.hasNext()) {
          reader.next();
        }
      }
      return new ResetJournal(metaStore, journal, reader, JournalEntry.readAll(journal));
    }

    void reset() {
      journal.reset(RESET_INDEX);
    }

    /** Closes the journal gracefully, which flushes it; closing it twice would unmap it twice. */
    void closeJournal() {
      if (closed) {
        return;
      }
      closed = true;
      if (reader != null) {
        reader.close();
      }
      journal.close();
    }

    @Override
    public void close() {
      closeJournal();
    }

    private static SegmentAllocator allocator(final ResetSteps steps) {
      final var posix = SegmentAllocator.posix(SegmentAllocator.noop());
      return (channel, fd, segmentSize) -> {
        // once full, the disk stays full: creating a segment retries after freeing space
        if (steps.before("allocate segment") || steps.isDiskFull()) {
          steps.fillDisk();
          throw new OutOfDiskSpace("Injected: not enough space to allocate a new journal segment");
        }
        posix.allocate(channel, fd, segmentSize);
        steps.after("allocate segment");
      };
    }

    private static Segment spySegment(final InvocationOnMock call, final ResetSteps steps)
        throws Throwable {
      final var segment = (Segment) call.callRealMethod();
      final var spy = Mockito.spy(segment);
      Mockito.doAnswer(
              delete -> {
                final var name = "delete segment " + segment.id();
                if (steps.before(name)) {
                  throw new JournalException("Injected: failed to delete segment " + segment.id());
                }
                delete.callRealMethod();
                steps.after(name);
                return null;
              })
          .when(spy)
          .delete();
      return spy;
    }
  }

  /** The steps of a reset that change what is on disk; one of them can be made to fail. */
  private static final class ResetSteps {
    static final int NO_FAILURE = -1;

    private final int failAt;
    private final List<String> names = new ArrayList<>();
    private Consumer<String> onStep = description -> {};
    private boolean active;
    private boolean diskFull;

    private ResetSteps(final int failAt) {
      this.failAt = failAt;
    }

    void onStep(final Consumer<String> onStep) {
      this.onStep = onStep;
    }

    /** Records the steps of the given action, and fails the one to fail. */
    void during(final Runnable action) {
      active = true;
      try {
        action.run();
      } finally {
        active = false;
        diskFull = false;
      }
    }

    List<String> names() {
      return List.copyOf(names);
    }

    /** Returns true if the step must fail. */
    boolean before(final String name) {
      if (!active) {
        return false;
      }
      onStep.accept("before " + name);
      names.add(name);
      return names.size() - 1 == failAt;
    }

    void after(final String name) {
      if (active) {
        onStep.accept("after " + name);
      }
    }

    void fillDisk() {
      diskFull = true;
    }

    boolean isDiskFull() {
      return active && diskFull;
    }
  }

  /** A last flushed index whose changes are steps of the reset. */
  private static final class StepMetaStore extends JournalMetaStore.InMemory {
    private final ResetSteps steps;

    private StepMetaStore(final ResetSteps steps) {
      this.steps = steps;
    }

    @Override
    public void storeLastFlushedIndex(final long index) {
      final var name = "store last flushed index " + index;
      if (steps.before(name)) {
        throw new JournalException("Injected: failed to store the last flushed index");
      }
      super.storeLastFlushedIndex(index);
      steps.after(name);
    }

    @Override
    public void resetLastFlushedIndex() {
      final var name = "reset last flushed index";
      if (steps.before(name)) {
        throw new JournalException("Injected: failed to reset the last flushed index");
      }
      super.resetLastFlushedIndex();
      steps.after(name);
    }
  }
}
