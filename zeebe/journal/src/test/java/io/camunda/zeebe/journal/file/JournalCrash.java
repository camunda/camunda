/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalMetaStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What a process crash leaves of a journal: the files in its directory and its last flushed index.
 * A copy of the files keeps every write to a mapped segment, as a process crash does; it does not
 * model a power loss, which can also lose what was not flushed.
 *
 * @param root the directory to open a journal on, with the segments in its {@code data} directory
 * @param metaStore holds the last flushed index at the time of the crash
 */
record JournalCrash(Path root, JournalMetaStore metaStore) {

  /** Copies the journal at {@code journalRoot} and its last flushed index to {@code target}. */
  static JournalCrash capture(
      final Path journalRoot, final JournalMetaStore metaStore, final Path target) {
    try {
      final var data = Files.createDirectories(target.resolve("data"));
      try (final var files = Files.list(journalRoot.resolve("data"))) {
        for (final var file : files.filter(Files::isRegularFile).toList()) {
          Files.copy(file, data.resolve(file.getFileName()));
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return new JournalCrash(target, copyOf(metaStore));
  }

  static JournalMetaStore copyOf(final JournalMetaStore metaStore) {
    final var copy = new JournalMetaStore.InMemory();
    copyLastFlushedIndex(metaStore, copy);
    return copy;
  }

  /** Copies the last flushed index, if there is one, as a restarted node reads it again. */
  static void copyLastFlushedIndex(final JournalMetaStore from, final JournalMetaStore to) {
    if (from.hasLastFlushedIndex()) {
      to.storeLastFlushedIndex(from.loadLastFlushedIndex());
    }
  }
}
