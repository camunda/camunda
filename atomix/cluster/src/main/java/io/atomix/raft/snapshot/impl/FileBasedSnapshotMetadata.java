/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.atomix.raft.snapshot.impl;

import io.atomix.raft.snapshot.SnapshotId;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.ZbLogger;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;

public final class FileBasedSnapshotMetadata implements SnapshotId {
  private static final Logger LOGGER = new ZbLogger(FileBasedSnapshotMetadata.class);
  private static final int METADATA_PARTS = 3;

  private final long index;
  private final long term;
  private final WallClockTimestamp timestamp;

  FileBasedSnapshotMetadata(final long index, final long term, final WallClockTimestamp timestamp) {
    this.index = index;
    this.term = term;
    this.timestamp = timestamp;
  }

  public static Optional<FileBasedSnapshotMetadata> ofPath(final Path path) {
    return ofFileName(path.getFileName().toString());
  }

  static Optional<FileBasedSnapshotMetadata> ofFileName(final String name) {
    final var parts = name.split("-");
    Optional<FileBasedSnapshotMetadata> metadata = Optional.empty();

    if (parts.length >= METADATA_PARTS) {
      try {
        final var index = Long.parseLong(parts[0]);
        final var term = Long.parseLong(parts[1]);
        final var timestamp = Long.parseLong(parts[2]);

        metadata =
            Optional.of(
                new FileBasedSnapshotMetadata(index, term, WallClockTimestamp.from(timestamp)));
      } catch (final NumberFormatException e) {
        LOGGER.warn("Failed to parse part of snapshot metadata", e);
      }
    }

    return metadata;
  }

  @Override
  public String getSnapshotIdAsString() {
    return String.format("%d-%d-%d", getIndex(), getTerm(), getTimestamp().unixTimestamp());
  }

  @Override
  public long getIndex() {
    return index;
  }

  @Override
  public WallClockTimestamp getTimestamp() {
    return timestamp;
  }

  public long getTerm() {
    return term;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getIndex(), getTerm(), getTimestamp());
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    final FileBasedSnapshotMetadata that = (FileBasedSnapshotMetadata) other;
    return getIndex() == that.getIndex()
        && getTerm() == that.getTerm()
        && Objects.equals(getTimestamp(), that.getTimestamp());
  }

  @Override
  public String toString() {
    return "DbSnapshotMetadata{"
        + "index="
        + index
        + ", term="
        + term
        + ", timestamp="
        + timestamp
        + '}';
  }
}
