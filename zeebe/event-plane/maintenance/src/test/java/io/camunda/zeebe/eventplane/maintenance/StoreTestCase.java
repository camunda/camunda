/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * A filesystem store that records writes, a controllable clock, and a Raft journal to take entries
 * from: after the test's setup, entries 1 to 15 exist, each with two records, so that entry {@code
 * i} holds the positions {@code 2i - 1} and {@code 2i}.
 */
abstract class StoreTestCase {

  static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  static final Duration TWO_DAYS = Duration.ofDays(2);
  static final Duration ONE_HOUR = Duration.ofHours(1);

  @TempDir Path storeDirectory;
  @TempDir Path journalDirectory;

  final TestClock clock = new TestClock();
  RecordingStore store;
  SegmentFixture segments;
  RaftJournal journal;
  List<Entry> entries;

  @BeforeEach
  void openStore() {
    // The fixture writes to the files, so that the store records only what maintenance does.
    final var files = new FileSystemStore(storeDirectory);
    store = new RecordingStore(files);
    segments = new SegmentFixture(files, storeDirectory, clock);
    journal = new RaftJournal(journalDirectory);
    entries = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      entries.add(journal.records(2, 64));
    }
  }

  @AfterEach
  void closeJournal() {
    journal.close();
  }

  /** The entries with these indexes, both included. */
  List<Entry> entries(final int firstIndex, final int lastIndex) {
    return entries.subList(firstIndex - 1, lastIndex);
  }
}
