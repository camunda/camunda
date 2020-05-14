/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.atomix.raft.impl.zeebe.snapshot;

import java.nio.file.Path;
import java.util.Objects;

final class SnapshotImpl implements Snapshot {
  private final long compactionBound;
  private final Path path;

  SnapshotImpl(final long compactionBound, final Path path) {
    this.compactionBound = compactionBound;
    this.path = path;
  }

  @Override
  public long getCompactionBound() {
    return compactionBound;
  }

  @Override
  public Path getPath() {
    return path;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getCompactionBound(), getPath());
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    final SnapshotImpl snapshot = (SnapshotImpl) other;
    return getCompactionBound() == snapshot.getCompactionBound()
        && getPath().equals(snapshot.getPath());
  }

  @Override
  public String toString() {
    return "SnapshotImpl{" + "path=" + path + ", compactionBound=" + compactionBound + '}';
  }
}
