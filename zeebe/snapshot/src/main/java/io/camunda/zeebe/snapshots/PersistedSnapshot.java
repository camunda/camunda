/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.VisibleForTesting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Represents a snapshot, which was persisted at the {@link PersistedSnapshotStore}. */
public interface PersistedSnapshot {

  /**
   * Returns the snapshot format version.
   *
   * @return the snapshot format version
   */
  int version();

  SnapshotId snapshotId();

  /**
   * Returns the snapshot index.
   *
   * <p>The snapshot index is the index of the state machine at the point at which the snapshot was
   * persisted.
   *
   * @return The snapshot index.
   */
  long getIndex();

  /**
   * Returns the snapshot term.
   *
   * <p>The snapshot term is the term of the state machine at the point at which the snapshot was
   * persisted.
   *
   * @return The snapshot term.
   */
  long getTerm();

  /**
   * Returns a new snapshot chunk reader for this snapshot. Chunk readers are meant to be one-time
   * use and as such don't have to be thread-safe.
   *
   * @return a new snapshot chunk reader
   */
  SnapshotChunkReader newChunkReader();

  /**
   * @return a path to the snapshot location
   */
  Path getPath();

  /**
   * @return path to checksum file
   */
  Path getChecksumPath();

  /**
   * Returns an implementation specific compaction bound, e.g. a log stream position, index etc.,
   * used during compaction
   *
   * @return the compaction upper bound
   */
  long getCompactionBound();

  /**
   * @return the identifier of the snapshot
   */
  String getId();

  /**
   * Returns the checksums of the snapshot files, which can be used to verify integrity.
   *
   * @return the checksum of the snapshot files
   */
  ImmutableChecksumsSFV getChecksums();

  /**
   * SnapshotMetadata includes information related to a snapshot.
   *
   * @return the metadata of the snapshot.
   */
  SnapshotMetadata getMetadata();

  /**
   * Reserves this snapshot. When the snapshot is reserved, it is not deleted until it is released.
   * The reservation status is not persisted. After a restart the snapshot will be in state
   * released.
   *
   * <p>Returns a SnapshotReservation if the snapshot exists and is successfully reserved. Fails
   * exceptionally if the snapshot does not exist.
   *
   * <p>To release the reservation use {@link SnapshotReservation#release()}
   *
   * @return future with SnapshotReservation
   */
  ActorFuture<SnapshotReservation> reserve();

  default boolean isBootstrap() {
    return getMetadata() != null && getMetadata().isBootstrap();
  }

  @VisibleForTesting
  boolean isReserved();

  /**
   * Note that this method is not synchronized with the actor, so the snapshot may be deleted
   * concurrently, returning only a partial list of files.
   *
   * @return the files that are part of this snapshot, indexed by filename. Useful for calling
   *     {@link RestorableSnapshotStore#restore(String, Map)}
   */
  default Map<String, Path> files() {
    final var map = new HashMap<String, Path>();
    try (final var stream = Files.list(getPath())) {
      stream.forEach(
          file -> {
            final var fileName = file.getFileName().toString();
            map.put(fileName, file);
          });

    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    map.put(getChecksumPath().getFileName().toString(), getChecksumPath());
    return map;
  }
}
