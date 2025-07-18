/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId.SnapshotParseResult.Invalid;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId.SnapshotParseResult.Parsed;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBasedSnapshotId implements SnapshotId {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedSnapshotId.class);
  private static final int SNAPSHOT_ID_PARTS = 5;
  private static final int PREV_SNAPSHOT_ID_PARTS = 4;

  private final long index;
  private final long term;
  private final long processedPosition;
  private final long exporterPosition;
  // To ensure snapshots taken by different replicas have unique Ids, we include the brokerId. For
  // backward compatibility we make it an optional. Can be eventually make it mandatory.
  private final Optional<Integer> brokerId;

  FileBasedSnapshotId(
      final long index,
      final long term,
      final long processedPosition,
      final long exporterPosition,
      final int brokerId) {
    this.index = index;
    this.term = term;
    this.processedPosition = processedPosition;
    this.exporterPosition = exporterPosition;
    this.brokerId = Optional.of(brokerId);
  }

  FileBasedSnapshotId(
      final long index,
      final long term,
      final long processedPosition,
      final long exporterPosition) {
    this.index = index;
    this.term = term;
    this.processedPosition = processedPosition;
    this.exporterPosition = exporterPosition;
    brokerId = Optional.empty();
  }

  static FileBasedSnapshotId forBoostrap(final int brokerId) {
    return new FileBasedSnapshotId(1, 1, 0, 0, brokerId);
  }

  public static SnapshotParseResult ofPath(final Path path) {
    return ofFileName(path.getFileName().toString());
  }

  public static SnapshotParseResult ofFileName(final String name) {
    final var parts = name.split("-");
    if (parts.length >= SNAPSHOT_ID_PARTS) {
      try {
        final var index = Long.parseLong(parts[0]);
        final var term = Long.parseLong(parts[1]);
        final var processedPosition = Long.parseLong(parts[2]);
        final var exporterPosition = Long.parseLong(parts[3]);
        final var brokerId = Integer.parseInt((parts[4]));

        return new Parsed(
            new FileBasedSnapshotId(
                index, term, processedPosition, exporterPosition, brokerId));
      } catch (final NumberFormatException e) {
        return new Invalid(new IllegalArgumentException("Failed to parse part of snapshot id", e));
      }
    } else if (parts.length == PREV_SNAPSHOT_ID_PARTS) {
      try {
        final var index = Long.parseLong(parts[0]);
        final var term = Long.parseLong(parts[1]);
        final var processedPosition = Long.parseLong(parts[2]);
        final var exporterPosition = Long.parseLong(parts[3]);
        final var brokerId = Integer.parseInt((parts[4]));

        return new Parsed(
            new FileBasedSnapshotId(index, term, processedPosition, exporterPosition, brokerId));
      } catch (final NumberFormatException e) {
        return new Invalid(new IllegalArgumentException("Failed to parse part of snapshot id", e));
      }
    } else {
      return new Invalid(
          new IllegalArgumentException(
              "Expected snapshot file format to be %%d-%%d-%%d-%%d-%%d, but was %s"
                  .formatted(name)));
    }
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
  public String getSnapshotIdAsString() {
    return brokerId
        .map(
            broker ->
                String.format(
                    "%d-%d-%d-%d-%d",
                    getIndex(), getTerm(), getProcessedPosition(), getExportedPosition(), broker))
        // For backward compatibility
        .orElse(
            String.format(
                "%d-%d-%d-%d",
                getIndex(), getTerm(), getProcessedPosition(), getExportedPosition()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, term, processedPosition, exporterPosition, brokerId.hashCode());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FileBasedSnapshotId that = (FileBasedSnapshotId) o;
    return index == that.index
        && term == that.term
        && processedPosition == that.processedPosition
        && exporterPosition == that.exporterPosition
        && brokerId.equals(that.brokerId);
  }

  @Override
  public String toString() {
    return "FileBasedSnapshotId{"
        + "index="
        + index
        + ", term="
        + term
        + ", processedPosition="
        + processedPosition
        + ", exporterPosition="
        + exporterPosition
        + ", brokerId="
        + brokerId
        + '}';
  }

  public sealed interface SnapshotParseResult permits Parsed, Invalid {
    FileBasedSnapshotId getOrThrow();

    record Parsed(FileBasedSnapshotId snapshotId) implements SnapshotParseResult {

      @Override
      public FileBasedSnapshotId getOrThrow() {
        return snapshotId;
      }
    }

    record Invalid(Exception exception) implements SnapshotParseResult {
      @Override
      public FileBasedSnapshotId getOrThrow() {
        throw new IllegalStateException("Snapshot id could not be parsed", exception);
      }
    }
  }
}
