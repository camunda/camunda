/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalRecord;
import java.util.ArrayList;
import java.util.List;

/** The fields of a journal record that identify it; the checksum covers its data. */
record JournalEntry(long index, long asqn, long checksum) {

  static JournalEntry of(final JournalRecord record) {
    return new JournalEntry(record.index(), record.asqn(), record.checksum());
  }

  /** Reads every entry the journal holds, from its first index. */
  static List<JournalEntry> readAll(final Journal journal) {
    final var entries = new ArrayList<JournalEntry>();
    try (final var reader = journal.openReader()) {
      while (reader.hasNext()) {
        entries.add(of(reader.next()));
      }
    }
    return entries;
  }
}
