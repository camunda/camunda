/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalException.OutOfDiskSpace;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.Tuple;
import org.agrona.CloseHelper;
import org.mockito.Mockito;

/**
 * Runs random sequences of journal operations against a simple model of the log, with faults: the
 * disk runs full and empties again, flushing the journal directory fails, which fails a segment
 * deletion after its file was renamed, and the node restarts gracefully or crashes. After every
 * operation, the journal must hold exactly the entries of the model.
 *
 * <p>The model knows what must survive: everything up to the last flushed index survives a crash,
 * an operation that fails for lack of disk space changes nothing, and a crash never undoes a reset
 * that returned. A crash opens a new journal on a {@link JournalCrash}.
 *
 * <p>A reader follows the log like Raft's readers do, so that a deleted segment it holds stays
 * mapped: if an append ever goes to a deleted segment, it then fails the property instead of
 * crashing the JVM.
 */
final class SegmentedJournalModelPropertyTest {

  private static final int ENTRIES_PER_SEGMENT = 4;
  private static final String ENTRY_DATA_FORMAT = "entry-%06d";

  /** Small scenarios with a fixed seed, so that they run the same on every build. */
  @Property(tries = 200, seed = "69536")
  void shouldMatchModelUnderFaults(@ForAll("operations") final List<Operation> operations)
      throws Exception {
    run(operations);
  }

  /** More and longer scenarios with a new seed on every run. */
  @Tag("randomized")
  @Property(tries = 2_000)
  void shouldMatchModelUnderFaultsInLongerScenarios(
      @ForAll("longerOperations") final List<Operation> operations) throws Exception {
    run(operations);
  }

  @Provide
  Arbitrary<List<Operation>> operations() {
    return operation().list().ofMinSize(1).ofMaxSize(30);
  }

  @Provide
  Arbitrary<List<Operation>> longerOperations() {
    return operation().list().ofMinSize(1).ofMaxSize(100);
  }

  private static Arbitrary<Operation> operation() {
    final var fraction = Arbitraries.doubles().between(0, 1);
    return Arbitraries.frequencyOf(
        Tuple.of(8, Arbitraries.integers().between(1, 3).map(Append::new)),
        Tuple.of(3, Arbitraries.just(new Flush())),
        Tuple.of(1, fraction.map(DeleteAfter::new)),
        Tuple.of(1, fraction.map(DeleteUntil::new)),
        Tuple.of(2, Arbitraries.integers().between(-2, 20).map(Reset::new)),
        Tuple.of(2, Arbitraries.of(true, false).map(SetDiskFull::new)),
        Tuple.of(2, Arbitraries.of(true, false).map(SetDirectoryFlushFailing::new)),
        Tuple.of(1, Arbitraries.just(new ReadConcurrently())),
        Tuple.of(1, Arbitraries.just(new Restart())),
        Tuple.of(1, Arbitraries.just(new Crash())));
  }

  private static void run(final List<Operation> operations) throws Exception {
    final var root = Files.createTempDirectory("journal-model");
    final var node = new Node(root);
    // flushing the directory has no seam of its own; the journal runs on this thread only
    try (final var fileUtil = Mockito.mockStatic(FileUtil.class, Mockito.CALLS_REAL_METHODS)) {
      fileUtil
          .when(() -> FileUtil.flushDirectory(Mockito.any()))
          .thenAnswer(
              call -> {
                if (node.disk.directoryFlushFails) {
                  throw new IOException("Injected: failed to flush the directory");
                }
                return call.callRealMethod();
              });
      // given - an empty journal
      node.open(new JournalMetaStore.InMemory());

      for (final var operation : operations) {
        // when
        operation.apply(node);

        // then - the journal holds exactly the entries of the model
        node.assertMatchesModel(operation);
      }
    } finally {
      node.closeJournal();
      FileUtil.deleteFolderIfExists(root);
    }
  }

  private sealed interface Operation
      permits Append,
          Flush,
          DeleteAfter,
          DeleteUntil,
          Reset,
          SetDiskFull,
          SetDirectoryFlushFailing,
          ReadConcurrently,
          Restart,
          Crash {
    void apply(Node node) throws Exception;
  }

  private record Append(int count) implements Operation {
    @Override
    public void apply(final Node node) {
      for (int i = 0; i < count; i++) {
        node.append();
      }
    }
  }

  private record Flush() implements Operation {
    @Override
    public void apply(final Node node) throws FlushException {
      node.journal.flush();
      node.model.flushAll();
    }
  }

  /**
   * Truncates the log after an entry between the first and the last one, or before the first one,
   * which removes every entry, as after a reset for a snapshot.
   */
  private record DeleteAfter(double fraction) implements Operation {
    @Override
    public void apply(final Node node) {
      if (node.model.isEmpty()) {
        return;
      }
      final long index = node.model.truncationIndexAt(fraction);
      try {
        node.journal.deleteAfter(index);
      } catch (final JournalException e) {
        // with a concurrent reader, the truncation also creates a segment
        assertThat(node.disk.full || node.disk.directoryFlushFails)
            .describedAs("disk is full or flushing fails, so the truncation may fail: %s", e)
            .isTrue();
        // Raft stops using the journal after a failed truncation, until the node restarts
        node.restart();
        node.model.recoverFromFailedTruncation(
            index, node.journal.getFirstIndex(), node.journal.getLastIndex());
        return;
      }
      node.model.truncateAfter(index);
    }
  }

  /** Compacts the log up to an entry between the first and the last one. */
  private record DeleteUntil(double fraction) implements Operation {
    @Override
    public void apply(final Node node) {
      if (node.model.isEmpty()) {
        return;
      }
      final long index = node.model.indexAt(fraction);
      node.journal.deleteUntil(index);
      // whole segments are deleted, so the journal decides where it starts now
      node.model.compactTo(node.journal.getFirstIndex(), index);
    }
  }

  /** Resets the log to start at an index around its end, as for a received snapshot. */
  private record Reset(int distanceFromNextIndex) implements Operation {
    @Override
    public void apply(final Node node) {
      final long index = node.model.indexAfterEnd(distanceFromNextIndex);
      try {
        node.journal.reset(index);
      } catch (final JournalException e) {
        assertThat(node.disk.full || node.disk.directoryFlushFails)
            .describedAs("disk is full or flushing fails, so the reset may fail: %s", e)
            .isTrue();
        if (node.disk.directoryFlushFails) {
          // deleting old segments may have failed after some were renamed
          node.model.recoverFromFailedReset(
              index, node.journal.getFirstIndex(), node.journal.getLastIndex());
        }
        // otherwise the reset failed before deleting anything, so the journal must be unchanged
        return;
      }
      node.model.reset(index);
      // Raft seeks its readers after a reset
      node.follow();
    }
  }

  private record SetDiskFull(boolean full) implements Operation {
    @Override
    public void apply(final Node node) {
      node.disk.full = full;
    }
  }

  private record SetDirectoryFlushFailing(boolean failing) implements Operation {
    @Override
    public void apply(final Node node) {
      node.disk.directoryFlushFails = failing;
    }
  }

  /**
   * Reads to the end of the log on another thread, so that a truncation must keep the records it
   * deletes readable; Raft's leader appender reads this way.
   */
  private record ReadConcurrently() implements Operation {
    @Override
    public void apply(final Node node) {
      node.readConcurrently();
    }
  }

  /** Closes the journal, which flushes it, and opens it again. */
  private record Restart() implements Operation {
    @Override
    public void apply(final Node node) {
      node.restart();
      node.model.flushAll();
      // compacted segments whose deletion failed are still on disk, and come back
      node.model.recoverTo(node.journal.getFirstIndex(), node.model.lastIndex());
    }
  }

  /** Opens a journal on what a crash leaves on disk, without closing the journal first. */
  private record Crash() implements Operation {
    @Override
    public void apply(final Node node) {
      final var crash =
          JournalCrash.capture(node.directory, node.factory.metaStore(), node.nextCrashDirectory());
      node.closeJournal();
      node.directory = crash.root();
      node.open(crash.metaStore());
      node.model.recoverTo(node.journal.getFirstIndex(), node.journal.getLastIndex());
    }
  }

  /**
   * The disk the journal writes to; when full, no segment can be allocated, and when flushing the
   * directory fails, creating or deleting a segment fails after its file was created or renamed.
   */
  private static final class Disk {
    private boolean full;
    private boolean directoryFlushFails;

    SegmentAllocator allocator() {
      final var posix = SegmentAllocator.posix(SegmentAllocator.noop());
      return (channel, fd, segmentSize) -> {
        if (full) {
          throw new OutOfDiskSpace("Injected: the disk is full");
        }
        posix.allocate(channel, fd, segmentSize);
      };
    }
  }

  /** The entries the journal must hold, and what each operation does to them. */
  private static final class LogModel {
    /** The index of the first entry since the last reset, compacted or not. */
    private long lineageStart = 1;

    /** The first index the journal holds. */
    private long firstIndex = 1;

    private final List<JournalEntry> entries = new ArrayList<>();
    private long flushedIndex;

    long lastIndex() {
      return lineageStart + entries.size() - 1;
    }

    boolean isEmpty() {
      return lastIndex() < firstIndex;
    }

    List<JournalEntry> held() {
      return entries.subList((int) (firstIndex - lineageStart), entries.size());
    }

    /** An index between the first and the last one; the model must not be empty. */
    long indexAt(final double fraction) {
      return firstIndex + Math.round(fraction * (lastIndex() - firstIndex));
    }

    /** An index from the one before the first entry to the last one; the model is not empty. */
    long truncationIndexAt(final double fraction) {
      return firstIndex - 1 + Math.round(fraction * (lastIndex() - firstIndex + 1));
    }

    /** An index around the end of the log, but not before its start. */
    long indexAfterEnd(final int distanceFromNextIndex) {
      return Math.max(firstIndex, lastIndex() + 1 + distanceFromNextIndex);
    }

    void append(final JournalEntry entry) {
      assertThat(entry.index())
          .describedAs("index of an appended entry")
          .isEqualTo(lastIndex() + 1);
      entries.add(entry);
    }

    void flushAll() {
      flushedIndex = lastIndex();
    }

    void truncateAfter(final long index) {
      entries.subList((int) (index - lineageStart + 1), entries.size()).clear();
      flushedIndex = Math.min(flushedIndex, index);
    }

    /** Compaction deleted whole segments up to the one holding {@code compactedIndex}. */
    void compactTo(final long newFirstIndex, final long compactedIndex) {
      assertThat(newFirstIndex)
          .describedAs("first index after compacting up to %d", compactedIndex)
          .isBetween(firstIndex, compactedIndex);
      firstIndex = newFirstIndex;
    }

    /** What was flushed survives a crash; what was not may be lost from the end. */
    void recoverTo(final long recoveredFirstIndex, final long recoveredLastIndex) {
      assertThat(recoveredLastIndex)
          .describedAs("last index after a crash")
          .isBetween(flushedIndex, lastIndex());
      assertThat(recoveredFirstIndex)
          .describedAs("first index after a crash")
          .isBetween(lineageStart, firstIndex);
      firstIndex = recoveredFirstIndex;
      truncateAfter(recoveredLastIndex);
    }

    /**
     * A truncation that failed while deleting segments, followed by a restart, leaves the entries
     * up to the end of a segment after the truncation index, as segments are deleted newest first;
     * the next truncation removes the rest. Compacted segments whose deletion failed come back.
     * Closing the journal flushed what is left.
     */
    void recoverFromFailedTruncation(
        final long truncationIndex, final long recoveredFirstIndex, final long recoveredLastIndex) {
      assertThat(recoveredLastIndex)
          .describedAs("last index after a failed truncation to %d", truncationIndex)
          .isBetween(truncationIndex, lastIndex());
      truncateAfter(recoveredLastIndex);
      flushAll();
      recoverTo(recoveredFirstIndex, recoveredLastIndex);
    }

    /**
     * A reset that failed while deleting old segments leaves either the entries before it, or a
     * prefix of them as old segments are deleted newest first, or the reset log once every old
     * segment is gone.
     */
    void recoverFromFailedReset(
        final long resetIndex, final long recoveredFirstIndex, final long recoveredLastIndex) {
      if (recoveredFirstIndex == resetIndex
          && recoveredLastIndex == resetIndex - 1
          && recoveredFirstIndex != firstIndex) {
        reset(resetIndex);
        return;
      }
      assertThat(recoveredFirstIndex)
          .describedAs("first index after a failed reset")
          .isEqualTo(firstIndex);
      assertThat(recoveredLastIndex)
          .describedAs("last index after a failed reset")
          .isBetween(firstIndex - 1, lastIndex());
      truncateAfter(recoveredLastIndex);
    }

    void reset(final long index) {
      entries.clear();
      lineageStart = index;
      firstIndex = index;
      flushedIndex = index - 1;
    }
  }

  /**
   * A node with its journal, its disk and the model of its log. The journal deletes and pre-creates
   * segments on the test thread, so that a scenario runs the same way every time.
   */
  private static final class Node {
    private final Disk disk = new Disk();
    private final LogModel model = new LogModel();
    private final Path root;
    private int crashes;
    private Path directory;
    private TestJournalFactory factory;
    private SegmentedJournal journal;
    private JournalReader follower;
    private JournalReader concurrentReader;
    private int written;

    private Node(final Path root) {
      this.root = root;
      directory = root.resolve("journal");
    }

    Path nextCrashDirectory() {
      return root.resolve("crash-" + ++crashes);
    }

    void open(final JournalMetaStore lastFlushedIndex) {
      factory =
          new TestJournalFactory(
              ENTRY_DATA_FORMAT.formatted(0), ENTRIES_PER_SEGMENT, disk.allocator());
      JournalCrash.copyLastFlushedIndex(lastFlushedIndex, factory.metaStore());
      journal =
          factory.journal(
              factory.directSegmentsManager(
                  directory, factory.segmentLoader(), factory.metaStore()));
      follower = journal.openReader();
      follow();
    }

    void append() {
      final var data = ENTRY_DATA_FORMAT.formatted(++written);
      try {
        model.append(
            JournalEntry.of(journal.append(new DirectBufferWriter(BufferUtil.wrapString(data)))));
      } catch (final JournalException e) {
        // only a roll-over can fail, and it creates the next segment before using it
        assertThat(disk.full || disk.directoryFlushFails)
            .describedAs("disk is full or flushing fails, so the append may fail: %s", e)
            .isTrue();
        return;
      }
      follow();
    }

    /** Lets the follower read to the end, so that it holds the last segment. */
    void follow() {
      follower.seek(model.firstIndex);
      while (follower.hasNext()) {
        follower.next();
      }
    }

    void assertMatchesModel(final Operation operation) {
      assertThat(JournalEntry.readAll(journal))
          .describedAs("entries after %s", operation)
          .isEqualTo(model.held());
      assertThat(journal.getFirstIndex())
          .describedAs("first index after %s", operation)
          .isEqualTo(model.firstIndex);
      assertThat(journal.getLastIndex())
          .describedAs("last index after %s", operation)
          .isEqualTo(model.lastIndex());
    }

    void readConcurrently() {
      if (concurrentReader == null) {
        concurrentReader = journal.openConcurrentReader();
      }
      concurrentReader.seek(model.firstIndex);
      while (concurrentReader.hasNext()) {
        concurrentReader.next();
      }
    }

    /** Closes the journal, which flushes it, and opens it again. */
    void restart() {
      // opening copies the last flushed index, so that it includes what closing the journal stored
      final var metaStore = factory.metaStore();
      closeJournal();
      open(metaStore);
    }

    void closeJournal() {
      CloseHelper.quietCloseAll(concurrentReader, follower, journal);
      concurrentReader = null;
      follower = null;
      journal = null;
    }
  }
}
