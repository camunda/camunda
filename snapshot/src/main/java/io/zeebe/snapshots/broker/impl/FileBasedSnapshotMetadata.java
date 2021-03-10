/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.broker.impl;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.snapshots.broker.SnapshotId;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBasedSnapshotMetadata implements SnapshotId {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedSnapshotMetadata.class);
  private static final int METADATA_PARTS = 5;
  private static final int METADATA_PARTS_OLD_VERSION = 3;

  private final long index;
  private final long term;
  private final WallClockTimestamp timestamp;
  private final long processedPosition;
  private final long exporterPosition;

  FileBasedSnapshotMetadata(
      final long index,
      final long term,
      final WallClockTimestamp timestamp,
      final long processedPosition,
      final long exporterPosition) {
    this.index = index;
    this.term = term;
    // We keep timestamp for backward compatibility
    this.timestamp = timestamp;
    this.processedPosition = processedPosition;
    this.exporterPosition = exporterPosition;
  }

  public static Optional<FileBasedSnapshotMetadata> ofPath(final Path path) {
    return ofFileName(path.getFileName().toString());
  }

  public static Optional<FileBasedSnapshotMetadata> ofFileName(final String name) {
    final var parts = name.split("-");
    Optional<FileBasedSnapshotMetadata> metadata = Optional.empty();

    if (parts.length >= METADATA_PARTS) {
      try {
        final var index = Long.parseLong(parts[0]);
        final var term = Long.parseLong(parts[1]);
        final var timestamp = Long.parseLong(parts[2]);
        final var processedPosition = Long.parseLong(parts[3]);
        final var exporterPosition = Long.parseLong(parts[4]);

        metadata =
            Optional.of(
                new FileBasedSnapshotMetadata(
                    index,
                    term,
                    WallClockTimestamp.from(timestamp),
                    processedPosition,
                    exporterPosition));
      } catch (final NumberFormatException e) {
        LOGGER.warn("Failed to parse part of snapshot metadata", e);
      }
    } else if (parts.length >= METADATA_PARTS_OLD_VERSION) {
      try {
        final var index = Long.parseLong(parts[0]);
        final var term = Long.parseLong(parts[1]);
        final var timestamp = Long.parseLong(parts[2]);

        metadata =
            Optional.of(
                new FileBasedSnapshotMetadata(
                    index, term, WallClockTimestamp.from(timestamp), 0, 0));
      } catch (final NumberFormatException e) {
        LOGGER.warn("Failed to parse part of snapshot metadata", e);
      }
    }
    return metadata;
  }

  @Override
  public long getIndex() {
    return index;
  }

  @Override
  public long getTerm() {
    return term;
  }

  @Override
  public long getProcessedPosition() {
    return processedPosition;
  }

  @Override
  public long getExportedPosition() {
    return exporterPosition;
  }

  @Override
  public WallClockTimestamp getTimestamp() {
    return timestamp;
  }

  @Override
  public String getSnapshotIdAsString() {
    return String.format(
        "%d-%d-%d-%d-%d",
        getIndex(),
        getTerm(),
        getTimestamp().unixTimestamp(),
        getProcessedPosition(),
        getExportedPosition());
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, term, processedPosition, exporterPosition);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FileBasedSnapshotMetadata that = (FileBasedSnapshotMetadata) o;
    return index == that.index
        && term == that.term
        && processedPosition == that.processedPosition
        && exporterPosition == that.exporterPosition;
  }

  @Override
  public String toString() {
    return "FileBasedSnapshotMetadata{"
        + "index="
        + index
        + ", term="
        + term
        + ", timestamp="
        + timestamp
        + ", processedPosition="
        + processedPosition
        + ", exporterPosition="
        + exporterPosition
        + '}';
  }
}
