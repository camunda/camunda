/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.utils.time.WallClockTimestamp;
import java.util.Objects;

public class DbSnapshotMetadata implements DbSnapshotId {
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
