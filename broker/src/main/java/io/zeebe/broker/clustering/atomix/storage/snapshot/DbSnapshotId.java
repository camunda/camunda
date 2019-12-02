/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import java.util.Comparator;

/** A {@link DbSnapshot}'s ID is simply a combination of its index and its position. */
public interface DbSnapshotId extends Comparable<DbSnapshotId> {

  long getIndex();

  long getPosition();

  /**
   * A snapshot is considered "lower" if its index is less than that of the other snapshot. If they
   * are the same, then it is considered "lower" if its position is less than that of the other
   * snapshot. If they are the same then these snapshots are the same order-wise.
   *
   * @param other the snapshot to compare against
   * @return -1 if {@code this} is less than {@code other}, 0 if they are the same, 1 if it is
   *     greater
   */
  @Override
  default int compareTo(final DbSnapshotId other) {
    return Comparator.comparingLong(DbSnapshotId::getIndex)
        .thenComparingLong(DbSnapshotId::getPosition)
        .compare(this, other);
  }
}
