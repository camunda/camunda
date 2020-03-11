/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides a layer of abstraction between concrete snapshot stores (i.e. Atomix's or any Raft
 * related snapshot store) and the logstream abstraction. Should be removed as part of a refactoring
 * to refine/define the Engine/Logstream abstractions.
 */
public interface SnapshotStorage extends AutoCloseable {

  /**
   * Returns snapshot for the given position with a temporary directory. This method is
   * non-deterministic, as it returns a directory with the current timestamp as part of the name,
   * and therefore should be cached if it needs to be reused.
   *
   * @param snapshotPosition the position to use
   * @return a pending snapshot
   */
  Snapshot getPendingSnapshotFor(long snapshotPosition);

  /**
   * Returns an existing, temporary working directory for a snapshot with the given ID; primarily
   * used during replication of snapshots to preserver the snapshot ID.
   *
   * @param id the snapshot ID
   * @return an existing path
   */
  Path getPendingDirectoryFor(String id);

  /**
   * Commits to the snapshot to the underlying store, making it permanently accessible. This may
   * trigger further side effects, such as deleting old snapshots, compacting, etc.
   *
   * @param snapshot the snapshot to commit
   * @return true if committed, false otherwise
   */
  default boolean commitSnapshot(final Snapshot snapshot) {
    return commitSnapshot(snapshot.getPath());
  }

  /**
   * Commits to the snapshot to the underlying store, making it permanently accessible. This may
   * trigger further side effects, such as deleting old snapshots, compacting, etc.
   *
   * @param snapshotPath the path to the snapshot to commit
   * @return true if committed, false otherwise
   */
  boolean commitSnapshot(Path snapshotPath);

  /**
   * Returns the latest snapshot if any.
   *
   * @return the latest snapshot or empty
   */
  Optional<Snapshot> getLatestSnapshot();

  /**
   * A stream of committed snapshots, ordered from oldest to newest.
   *
   * @return a stream of committed snapshots
   */
  Stream<Snapshot> getSnapshots();

  /**
   * Returns the current runtime directory, pointing to the database that should be used by the
   * engine, which may or may not exist yet.
   *
   * @return path to the runtime directory
   */
  Path getRuntimeDirectory();

  /**
   * Returns true if a snapshot with the given ID exists already, false otherwise.
   *
   * @param id the snapshot ID to look for
   * @return true if there is a committed snapshot with this ID, false otherwise
   */
  boolean exists(String id);

  @Override
  void close();

  /**
   * Registers a listener which is called whenever snapshots are purged.
   *
   * @param listener the new listener
   */
  void addDeletionListener(SnapshotDeletionListener listener);

  /**
   * Unregisters a deletion listener; should do nothing if not already registered.
   *
   * @param listener the listener to remove
   */
  void removeDeletionListener(SnapshotDeletionListener listener);

  /**
   * Returns a collection of snapshot related metrics, useful to observe snapshot and replication
   * operations.
   *
   * @return a pre-instantiated metrics interface
   */
  SnapshotMetrics getMetrics();
}
