/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalException.OutOfDiskSpace;
import io.camunda.zeebe.journal.JournalReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exploratory repro: out-of-disk while resetting the journal for a received snapshot. */
final class ResetOutOfDiskReproTest {

  private @TempDir Path directory;

  @ParameterizedTest(name = "openReader={0}")
  @ValueSource(booleans = {false, true})
  void explore(final boolean keepReaderOpen) throws Exception {
    final var outOfDisk = new AtomicBoolean(false);
    final SegmentAllocator allocator =
        (channel, fd, size) -> {
          if (outOfDisk.get()) {
            throw new OutOfDiskSpace("Nope, no free space.");
          }
          SegmentAllocator.noop().allocate(channel, fd, size);
        };
    final var factory = new TestJournalFactory("test", 4, allocator);
    var journal = factory.journal(factory.segmentsManager(directory));

    for (int i = 1; i <= 10; i++) {
      journal.append(i, factory.entry());
    }
    journal.flush();
    final JournalReader reader = keepReaderOpen ? journal.openReader() : null;
    if (reader != null) {
      // like Raft's appender/log stream readers, sit at the tail so the tail segment stays mapped
      reader.seek(10);
      reader.next();
    }
    print("after append+flush", journal, factory);

    // follower receives a snapshot at index 20 -> reset(21), but the disk is full
    outOfDisk.set(true);
    try {
      journal.reset(21);
      System.out.println("reset unexpectedly succeeded");
    } catch (final Exception e) {
      System.out.println("reset failed: " + e);
    }
    // in production checkDiskSpace() throws before Files.createFile(); our allocator throws after
    // it, leaving an empty file behind that production would not have -> remove it
    removeEmptyFiles();
    print("after failed reset", journal, factory);
    try {
      journal.openReader().close();
      System.out.println("openReader ok");
    } catch (final Exception e) {
      System.out.println("openReader failed: " + e);
    }

    // Raft rolls back the snapshot; leader keeps replicating. Try to append the next entry
    // at whatever the journal thinks its last index is.
    if (!keepReaderOpen) {
      System.out.println("skipping append: segment is unmapped, append would SIGSEGV the JVM");
    } else
      try {
        final var next = journal.getLastIndex() + 1;
        journal.append(factory.entry());
        journal.flush();
        System.out.println("append after failed reset ok, appended index " + next);
        // disk still full; keep replicating so the writer rolls into the pre-created segment (the
        // async preparation of the next one fails, as seen in the broker logs)
        for (int i = 0; i < 4; i++) {
          journal.append(factory.entry());
        }
        journal.flush();
        System.out.println("appended 4 more, last index " + journal.getLastIndex());
      } catch (final Throwable e) {
        System.out.println("append after failed reset failed: " + e);
      }
    print("after append attempt", journal, factory);

    // restart (disk space freed by then, as on the real broker)
    outOfDisk.set(false);
    if (reader != null) {
      reader.close();
    }
    try {
      journal.close();
    } catch (final Throwable e) {
      System.out.println("close failed: " + e);
    }
    removeEmptyFiles();
    print("after close", journal, factory);
    try {
      journal = factory.journal(factory.segmentsManager(directory));
      print("after restart", journal, factory);
      journal.close();
    } catch (final Throwable e) {
      System.out.println("restart failed: " + e);
      e.printStackTrace(System.out);
    }
  }

  private void removeEmptyFiles() throws IOException {
    try (final Stream<Path> list = Files.list(directory.resolve("data"))) {
      for (final Path p : list.toList()) {
        if (Files.size(p) == 0) {
          System.out.println("removing test-only empty file " + p.getFileName());
          Files.delete(p);
        }
      }
    }
  }

  private void print(
      final String step, final SegmentedJournal journal, final TestJournalFactory factory)
      throws IOException {
    final var meta = factory.metaStore();
    final String files;
    try (final Stream<Path> list = Files.list(directory.resolve("data"))) {
      files = list.map(p -> p.getFileName().toString()).sorted().toList().toString();
    }
    long first;
    long last;
    try {
      first = journal.getFirstIndex();
      last = journal.getLastIndex();
    } catch (final Throwable e) {
      System.out.printf("== %s: index lookup failed %s%n", step, e);
      first = last = Long.MIN_VALUE;
    }
    System.out.printf(
        "== %s: first=%d last=%d hasFlushed=%s lastFlushed=%d files=%s%n",
        step, first, last, meta.hasLastFlushedIndex(), meta.loadLastFlushedIndex(), files);
  }
}
