/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import io.zeebe.db.ZeebeDb;
import io.zeebe.snapshots.raft.TransientSnapshot;
import java.util.Optional;

public interface StateController extends AutoCloseable {
  /**
   * Takes a snapshot based on the given position. The position is a last processed lower bound
   * event position.
   *
   * @param lowerBoundSnapshotPosition the lower bound snapshot position
   * @return a pending snapshot, or nothing if the operation fails
   */
  Optional<TransientSnapshot> takeTransientSnapshot(long lowerBoundSnapshotPosition);

  /** Registers to consumes replicated snapshots. */
  void consumeReplicatedSnapshots();

  /**
   * Recovers the state from the latest snapshot.
   *
   * @param shouldForceCompactionAfterRecovery if true a manual compaction on the state is
   *     triggered.
   */
  void recover(final boolean shouldForceCompactionAfterRecovery) throws Exception;

  /**
   * Opens the database from the latest snapshot.
   *
   * @return an opened database
   */
  ZeebeDb openDb();

  /**
   * Returns the current number of valid snapshots.
   *
   * @return valid snapshots count
   */
  int getValidSnapshotsCount();
}
