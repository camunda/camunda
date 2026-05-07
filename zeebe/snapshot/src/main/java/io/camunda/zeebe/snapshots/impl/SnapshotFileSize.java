/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Computes the total bytes of the data files in a snapshot directory. Used at three points where
 * {@code totalSizeBytes} needs to be established for a snapshot whose metadata does not carry it:
 *
 * <ul>
 *   <li>persisting a fresh transient snapshot (computed from the files just written by the snapshot
 *       callback)
 *   <li>persisting a received snapshot whose sender did not include {@code totalSizeBytes} in the
 *       metadata it sent
 *   <li>opening the snapshot store and discovering a previously-persisted snapshot whose on-disk
 *       metadata predates the field
 * </ul>
 *
 * Excludes the snapshot metadata file (which is written separately and would create a circular
 * dependency with the value being computed) and the checksum file (which lives outside the snapshot
 * directory).
 */
final class SnapshotFileSize {

  private SnapshotFileSize() {
    throw new IllegalStateException("Utility class");
  }

  static long computeFromDirectory(final Path snapshotDirectory) throws IOException {
    try (final var files = Files.list(snapshotDirectory)) {
      return files
          .filter(
              p ->
                  !p.getFileName().toString().equals(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME))
          .mapToLong(SnapshotFileSize::sizeOf)
          .sum();
    }
  }

  private static long sizeOf(final Path file) {
    try {
      return Files.size(file);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
