/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import io.camunda.zeebe.journal.file.SegmentedJournal;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic journal workload for an exact-syscall crash sweep on LazyFS (8.8 journal).
 *
 * <p>run <dir> <armStep> <faultSpec>: executes a fixed list of steps (follower appends + flush,
 * leader appends without flush, deleteAfter across a segment boundary, compaction, reset). Before
 * step armStep it writes faultSpec (a LazyFS "lazyfs::crash::..." command) to the fault FIFO, so
 * LazyFS kills itself at the next matching syscall. Prints the same durability promises as
 * JournalCrash (ACK/TRUNC/RESET), "STEP k <name>" before each step and "END" when done.
 *
 * <p>steps <dir>: prints the step list.
 */
public final class CrashSweep {
  static final PrintStream OUT = new PrintStream(System.out, true);
  static final String FIFO = "/tmp/faults.fifo";

  record Step(String name, Op op) {}

  interface Op {
    void run(SegmentedJournal journal) throws Exception;
  }

  public static void main(final String[] args) throws Exception {
    final var steps = steps();
    if (args[0].equals("steps")) {
      for (int i = 0; i < steps.size(); i++) {
        OUT.println(i + " " + steps.get(i).name());
      }
      return;
    }
    final Path dir = Path.of(args[1]);
    final int arm = Integer.parseInt(args[2]);
    String fault = args[3];
    final var journal = JournalCrash.open(dir, System.getProperty("sweep.meta", "lazy"));
    for (int i = 0; i < steps.size(); i++) {
      if (i == arm) {
        // torn faults name one file by its path on LazyFS's backing directory
        final Path cur = journal.getTailSegments(journal.getFirstIndex()).lastEntry().getValue();
        final String curName = cur.getFileName().toString();
        final long curId = Long.parseLong(curName.substring(curName.lastIndexOf('-') + 1, curName.indexOf(".log")));
        final Path next = cur.resolveSibling("journal-" + (curId + 1) + ".log");
        final String spec =
            fault
                .replace("{CUR}", backing(cur))
                .replace("{NEXT}", backing(next))
                .replace("{META}", backing(dir.resolve("journal.meta")));
        try (final var fifo = new FileOutputStream(FIFO)) {
          fifo.write((spec + "\n").getBytes(StandardCharsets.UTF_8));
        }
        fault = spec;
        Thread.sleep(150); // let the LazyFS fault worker register it
        OUT.println("ARMED " + fault);
      }
      OUT.println("STEP " + i + " " + steps.get(i).name());
      steps.get(i).op().run(journal);
    }
    Thread.sleep(500); // background segment preparation
    OUT.println("END");
    Thread.sleep(Long.MAX_VALUE);
  }

  /** Maps a path on the LazyFS mount to the same file in LazyFS's backing (root) directory. */
  static String backing(final Path onMount) {
    return onMount
        .toAbsolutePath()
        .toString()
        .replaceFirst("^" + System.getProperty("sweep.mnt", "/mnt/lazy"), System.getProperty("sweep.root", "/var/lazy-root"));
  }

  static List<Step> steps() {
    final var random = new Random(7);
    final var steps = new ArrayList<Step>();
    final long[] mark = new long[1];
    // follower: append and acknowledge, crossing several segment boundaries
    for (int i = 0; i < 8; i++) {
      steps.add(new Step("follower-append-flush", j -> followerBatch(j, random)));
    }
    // leader: append without flushing across a segment boundary, then a new leader truncates
    // inside the newest segment while the flushed index is in the previous one (deleteAfter bug)
    steps.add(new Step("leader-append-noflush", j -> {
      mark[0] = j.getLastIndex();
      final long seg = lastSegmentStart(j);
      while (lastSegmentStart(j) == seg) {
        JournalCrash.appendBatch(j, random, 1);
      }
      JournalCrash.appendBatch(j, random, 5);
    }));
    steps.add(new Step("deleteAfter-into-new-segment", j -> {
      // -Dsweep.crossTruncate=false: truncate in the previous segment instead (no known bug)
      final long to =
          Boolean.parseBoolean(System.getProperty("sweep.crossTruncate", "true"))
              ? lastSegmentStart(j) + 2
              : lastSegmentStart(j) - 2;
      j.deleteAfter(to);
      j.flush();
      OUT.println("TRUNC " + to);
    }));
    for (int i = 0; i < 4; i++) {
      steps.add(new Step("follower-append-flush", j -> followerBatch(j, random)));
    }
    // leader appends and commits (flush on commit)
    steps.add(new Step("leader-append-commit", j -> {
      JournalCrash.appendBatch(j, random, 25);
      j.flush();
      OUT.println("ACK " + j.getLastIndex());
    }));
    // compaction after a snapshot: delete segments before the middle of the log
    steps.add(new Step("deleteUntil-compaction", j -> {
      final long until = (j.getFirstIndex() + j.getLastIndex()) / 2;
      OUT.println("COMPACT " + until + " deleted=" + j.deleteUntil(until));
    }));
    for (int i = 0; i < 3; i++) {
      steps.add(new Step("follower-append-flush", j -> followerBatch(j, random)));
    }
    // leader truncation inside the same segment (common case)
    steps.add(new Step("leader-append-noflush", j -> JournalCrash.appendBatch(j, random, 6)));
    steps.add(new Step("deleteAfter-same-segment", j -> {
      final long to = j.getLastIndex() - 3;
      j.deleteAfter(to);
      j.flush();
      OUT.println("TRUNC " + to);
    }));
    // snapshot install: reset the log beyond its end, then continue as follower
    steps.add(new Step("reset-snapshot-install", j -> {
      final long next = j.getLastIndex() + 100;
      j.reset(next);
      OUT.println("RESET " + next);
    }));
    for (int i = 0; i < 5; i++) {
      steps.add(new Step("follower-append-flush", j -> followerBatch(j, random)));
    }
    return steps;
  }

  static void followerBatch(final SegmentedJournal journal, final Random random) throws Exception {
    JournalCrash.appendBatch(journal, random, 3 + random.nextInt(10));
    journal.flush();
    OUT.println("ACK " + journal.getLastIndex());
  }

  static long lastSegmentStart(final SegmentedJournal journal) {
    return journal.getTailSegments(journal.getFirstIndex()).lastKey();
  }
}
