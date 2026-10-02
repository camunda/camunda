/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.CloseableSilently;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a store, which allows to persist snapshots on a storage, which is implementation
 * dependent. It can receive {@link SnapshotChunk}'s from an already {@link PersistedSnapshot} and
 * persist them in this current store.
 *
 * <p>Only one {@link PersistedSnapshot} at a time is stored in the {@link PersistedSnapshotStore}
 * and can be received via {@link PersistedSnapshotStore#getLatestSnapshot()}.
 */
public interface PersistedSnapshotStore extends CloseableSilently, BootstrapSnapshotStore {

  /**
   * Returns true if the given identifier is equal to the snapshot id of the current persisted
   * snapshot, false otherwise.
   *
   * @param id the snapshot Id to look for
   * @return true if the current snapshot has the equal Id, false otherwise
   * @see SnapshotId#getSnapshotIdAsString()
   */
  boolean hasSnapshotId(String id);

  /**
   * @return the latest {@link PersistedSnapshot} if exists
   */
  Optional<PersistedSnapshot> getLatestSnapshot();

  /** Returns a set of all available snapshots. */
  ActorFuture<Set<PersistedSnapshot>> getAvailableSnapshots();

  /** Returns the lowest compaction bound of all available snapshots. */
  ActorFuture<Long> getCompactionBound();

  /**
   * Reserves the latest snapshot for the given checkpoint in one step, so it cannot be deleted
   * between selecting and reserving it.
   *
   * <p>Reservations are made on behalf of a checkpoint and kept per checkpoint and snapshot, so a
   * checkpoint can only see and release reservations made for it: a checkpoint written before
   * {@link #releaseAllReservations()} cannot consume a reservation a newer checkpoint made for the
   * same snapshot. Reserving a snapshot again for the same checkpoint shares the reservation.
   *
   * @return future completed with the id of the reserved snapshot, or empty if there is none
   */
  ActorFuture<Optional<String>> reserveLatestSnapshot(long checkpointId);

  /**
   * Reserves the snapshot with the given id for the given checkpoint.
   *
   * @return future completed once the snapshot is reserved, or completed exceptionally with a
   *     {@link SnapshotException.SnapshotNotFoundException} if it does not exist (anymore)
   */
  ActorFuture<Void> reserveSnapshot(long checkpointId, String snapshotId);

  /**
   * @return future completed with the snapshot with the given id if it is currently reserved for
   *     the given checkpoint, empty otherwise
   */
  ActorFuture<Optional<PersistedSnapshot>> getReservedSnapshot(
      long checkpointId, String snapshotId);

  /**
   * Releases the reservation of the snapshot with the given id made for the given checkpoint, even
   * if it was reserved more than once; no-op if there is none.
   */
  void releaseReservation(long checkpointId, String snapshotId);

  /** Releases every reservation made via this interface. */
  void releaseAllReservations();

  /**
   * Purges all ongoing pending/transient/volatile snapshots.
   *
   * @return future which will be completed when all pending snapshots are deleted
   */
  ActorFuture<Void> abortPendingSnapshots();

  /**
   * Adds an {@link PersistedSnapshotListener} to the store, which is notified when a new {@link
   * PersistedSnapshot} is persisted at this store.
   *
   * @param listener the listener which should be added and notified later
   */
  ActorFuture<Boolean> addSnapshotListener(PersistedSnapshotListener listener);

  /**
   * Removes an registered {@link PersistedSnapshotListener} from the store. The listener will no
   * longer called when a new {@link PersistedSnapshot} is persisted at this store.
   *
   * @param listener the listener which should be removed
   */
  ActorFuture<Boolean> removeSnapshotListener(PersistedSnapshotListener listener);

  /**
   * @return the snapshot index of the latest {@link PersistedSnapshot}
   * @see PersistedSnapshotStore#getLatestSnapshot()
   */
  long getCurrentSnapshotIndex();

  /**
   * Deletes a {@link PersistedSnapshotStore} from disk.
   *
   * <p>The snapshot store will be deleted by simply reading {@code snapshot} file names from disk
   * and deleting snapshot files directly. Deleting the snapshot store does not involve reading any
   * snapshot files into memory.
   */
  ActorFuture<Void> delete();

  Path getPath();
}
