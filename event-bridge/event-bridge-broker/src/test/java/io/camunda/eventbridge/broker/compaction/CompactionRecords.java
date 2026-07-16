/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Record builders and clean-set readback shared across the compaction tests. */
final class CompactionRecords {

  private CompactionRecords() {}

  /** A keyed put record (timestamp mirrors position for deterministic tests). */
  static CompactionRecord put(final long position, final String key, final String value) {
    return new CompactionRecord(
        position,
        position,
        key.getBytes(StandardCharsets.UTF_8),
        value.getBytes(StandardCharsets.UTF_8),
        true);
  }

  /** A keyed tombstone (empty value). */
  static CompactionRecord tombstone(final long position, final String key) {
    return new CompactionRecord(
        position, position, key.getBytes(StandardCharsets.UTF_8), new byte[0], true);
  }

  /** A key-less record (legal pre-validation; never compacted). */
  static CompactionRecord unkeyed(final long position, final String value) {
    return new CompactionRecord(
        position, position, new byte[0], value.getBytes(StandardCharsets.UTF_8), false);
  }

  static String value(final CompactionRecord record) {
    return new String(record.value(), StandardCharsets.UTF_8);
  }

  static String key(final CompactionRecord record) {
    return new String(record.key(), StandardCharsets.UTF_8);
  }

  /** Reads the full committed clean set from disk, in position order. */
  static List<CompactionRecord> readCleanSet(final Path directory, final ManifestStore store) {
    final List<CompactionRecord> out = new ArrayList<>();
    store
        .latest()
        .ifPresent(
            manifest -> {
              for (final CleanSegment segment : manifest.segments()) {
                final var reader = new CleanSegmentReader(directory.resolve(segment.fileName()));
                while (reader.hasNext()) {
                  out.add(reader.next());
                }
              }
            });
    return out;
  }
}
