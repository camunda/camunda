/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.spi;

import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.state.Snapshot;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public interface SnapshotController extends AutoCloseable {
  /**
   * Takes a snapshot based on the given position and immediately commits it.
   *
   * @param lowerBoundSnapshotPosition the lower bound snapshot position
   */
  Snapshot takeSnapshot(long lowerBoundSnapshotPosition);

  /**
   * Takes a snapshot based on the given position. The position is a last processed lower bound
   * event position.
   *
   * @param lowerBoundSnapshotPosition the lower bound snapshot position
   */
  Snapshot takeTempSnapshot(long lowerBoundSnapshotPosition);

  /**
   * Commits the given temporary snapshot to the underlying storage.
   *
   * @param snapshot the snapshot to commit
   * @throws IOException thrown if moving the snapshot fails
   */
  void commitSnapshot(Snapshot snapshot) throws IOException;

  /**
   * Replicates the latest valid snapshot. The given executor is called for each snapshot chunk in
   * the latest snapshot. The executor should execute/run the given Runnable in a specific
   * environment (e.g. ActorThread).
   *
   * @param executor executor which executed the given Runnable
   */
  void replicateLatestSnapshot(Consumer<Runnable> executor);

  /** Registers to consumes replicated snapshots. */
  void consumeReplicatedSnapshots();

  /**
   * Recovers the state from the latest snapshot and returns the lower bound snapshot position.
   *
   * @return the lower bound position related to the snapshot
   */
  long recover() throws Exception;

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

  /**
   * Returns the position of the last valid snapshot. Or, -1 if no valid snapshot exists.
   *
   * @return the snapshot position
   */
  long getLastValidSnapshotPosition();

  /**
   * Returns the latest valid snapshot's directory.
   *
   * @return the latest valid snapshot's directory
   */
  File getLastValidSnapshotDirectory();
}
