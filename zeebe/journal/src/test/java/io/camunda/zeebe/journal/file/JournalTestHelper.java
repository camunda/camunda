/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class JournalTestHelper {
  static final UnsafeBuffer DATA = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  static final BufferWriter RECORD_DATA_WRITER = new DirectBufferWriter().wrap(DATA);
  static final String JOURNAL_NAME = "journal";
  static final char PART_SEPARATOR = '-';
  static final char EXTENSION_SEPARATOR = '.';
  static final String EXTENSION = "log";
  static final String DATA_DIRECTORY = "data";

  static void appendJournalEntries(
      final SegmentedJournal journal, final BufferWriter entry, final int... asqns) {
    Arrays.stream(asqns).forEach(asqn -> journal.append(asqn, entry));
  }

  static void appendJournalEntries(final SegmentedJournal journal, final int... asqns) {
    Arrays.stream(asqns).forEach(asqn -> journal.append(asqn, RECORD_DATA_WRITER));
  }

  static SegmentedJournal openJournal(
      final TestJournalFactory journalFactory, final Path directory) {
    return journalFactory.journal(journalFactory.segmentsManager(directory));
  }

  static void mergeJournals(final Path directory, final Path... directories) {
    mergeJournals(directory, JOURNAL_NAME, directories);
  }

  /**
   * Merge segments from other directories into the current journal's data directory, incrementing
   * the id to simulate later segments.
   *
   * @param directory primary directory where others will be merged into
   * @param journalName
   * @param directories
   */
  static void mergeJournals(
      final Path directory, final String journalName, final Path... directories) {
    final var dataDir = directory.resolve(DATA_DIRECTORY);
    int maxSegmentId =
        Arrays.stream(dataDir.toFile().listFiles(f -> Files.isRegularFile(f.toPath())))
            .map(File::getName)
            .map(SegmentFile::getSegmentIdFromPath)
            .max(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalStateException("No segments found in data directory."));

    for (final Path dir : directories) {
      final List<File> files =
          Arrays.stream(
                  dir.resolve(DATA_DIRECTORY)
                      .toFile()
                      .listFiles(f -> Files.isRegularFile(f.toPath())))
              .sorted(Comparator.comparing(f -> SegmentFile.getSegmentIdFromPath(f.getName())))
              .toList();
      if (!files.isEmpty()) {
        for (final File file : files) {
          final String newName =
              journalName + PART_SEPARATOR + (++maxSegmentId) + EXTENSION_SEPARATOR + EXTENSION;
          try {
            Files.move(file.toPath(), dataDir.resolve(newName));
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }
    }
  }
}
