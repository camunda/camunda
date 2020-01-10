/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.ZbLogger;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;

public final class DbSnapshotMetadata implements DbSnapshotId {
  private static final Logger LOGGER = new ZbLogger(DbSnapshotMetadata.class);

  private final long index;
  private final long term;
  private final WallClockTimestamp timestamp;
  private final long position;

  public DbSnapshotMetadata(
      final long index, final long term, final WallClockTimestamp timestamp, final long position) {
    this.index = index;
    this.term = term;
    this.timestamp = timestamp;
    this.position = position;
  }

  public static Optional<DbSnapshotMetadata> ofPath(final Path path) {
    return ofFileName(path.getFileName().toString());
  }

  public static Optional<DbSnapshotMetadata> ofFileName(final String name) {
    final var parts = name.split("-", 4);
    Optional<DbSnapshotMetadata> metadata = Optional.empty();

    if (parts.length == 4) {
      try {
        final var index = Long.parseLong(parts[0]);
        final var term = Long.parseLong(parts[1]);
        final var timestamp = Long.parseLong(parts[2]);
        final var position = Long.parseLong(parts[3]);

        metadata =
            Optional.of(
                new DbSnapshotMetadata(index, term, WallClockTimestamp.from(timestamp), position));
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
  public long getPosition() {
    return position;
  }

  public long getTerm() {
    return term;
  }

  public WallClockTimestamp getTimestamp() {
    return timestamp;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getIndex(), getTerm(), getTimestamp(), getPosition());
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    final DbSnapshotMetadata that = (DbSnapshotMetadata) other;
    return getIndex() == that.getIndex()
        && getTerm() == that.getTerm()
        && getPosition() == that.getPosition()
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
        + ", position="
        + position
        + '}';
  }
}
