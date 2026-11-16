/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Transient write error on a real kernel filesystem (8.8 journal). Follower-like loop: append,
 * flush, acknowledge only if the flush succeeded. A failed flush is not fatal (as in PassiveRole):
 * the entries stay in the log and the next flush covers them again.
 *
 * <p>run <dir>: phase 1 appends+flushes, prints SEGMENT <path> and waits for <dir>/../go-error;
 * phase 2 appends+flushes while writes fail; waits for <dir>/../go-ok; phase 3 appends+flushes
 * again; closes the journal (forces .meta) and prints END.
 */
public final class FsyncGate {
  static final PrintStream OUT = new PrintStream(System.out, true);

  public static void main(final String[] args) throws Exception {
    final Path dir = Path.of(args[1]);
    final Path ctl = dir.getParent();
    final var random = new Random(3);
    final SegmentedJournal journal = JournalCrash.open(dir, "lazy", 4 * 1024 * 1024);
    for (int i = 0; i < 20; i++) {
      step(journal, random);
    }
    OUT.println("SEGMENT " + journal.getTailSegments(journal.getFirstIndex()).lastEntry().getValue());
    waitFor(ctl.resolve("go-error"));
    for (int i = 0; i < 5; i++) {
      step(journal, random);
    }
    OUT.println("WINDOW_DONE");
    waitFor(ctl.resolve("go-ok"));
    for (int i = 0; i < 20; i++) {
      step(journal, random);
    }
    journal.close();
    OUT.println("CLOSED lastIndex promised above");
    OUT.println("END");
  }

  static void step(final SegmentedJournal journal, final Random random) {
    JournalCrash.appendBatch(journal, random, 5);
    try {
      journal.flush();
      OUT.println("ACK " + journal.getLastIndex());
    } catch (final FlushException e) {
      OUT.println("FLUSH_FAILED at lastIndex " + journal.getLastIndex() + ": " + e.getCause());
    }
  }

  static void waitFor(final Path file) throws InterruptedException {
    while (!Files.exists(file)) {
      Thread.sleep(50);
    }
  }
}
