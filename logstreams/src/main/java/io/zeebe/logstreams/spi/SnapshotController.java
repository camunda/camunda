/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.spi;

import io.atomix.raft.impl.zeebe.snapshot.Snapshot;
import io.atomix.raft.impl.zeebe.snapshot.SnapshotStorage;
import io.zeebe.db.ZeebeDb;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public interface SnapshotController extends AutoCloseable {
  /**
   * Takes a snapshot based on the given position and immediately commits it.
   *
   * @param lowerBoundSnapshotPosition the lower bound snapshot position
   * @return a committed snapshot, or nothing if the operation failed
   * @see SnapshotStorage#commitSnapshot(Path)
   * @see SnapshotStorage#getPendingSnapshotFor(long)
   */
  Optional<Snapshot> takeSnapshot(long lowerBoundSnapshotPosition);

  /**
   * Takes a snapshot based on the given position. The position is a last processed lower bound
   * event position.
   *
   * @param lowerBoundSnapshotPosition the lower bound snapshot position
   * @return a pending snapshot, or nothing if the operation fails
   * @see SnapshotStorage#getPendingSnapshotFor(long)
   */
  Optional<Snapshot> takeTempSnapshot(long lowerBoundSnapshotPosition);

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
  void recover() throws Exception;

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
   * Returns the latest valid snapshot's directory.
   *
   * @return the latest valid snapshot's directory
   */
  File getLastValidSnapshotDirectory();
}
