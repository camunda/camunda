/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Snapshot reservations made on behalf of checkpoints, tracked per checkpoint and snapshot. Each
 * reserve takes its own reservation of the snapshot, so the snapshot holds the only count, and it
 * stays reserved until every reserve was matched by a release.
 *
 * <p>Not thread-safe: must only be used on the snapshot store's actor.
 */
final class CheckpointReservations {

  private final Map<Key, Reservation> reservations = new HashMap<>();

  /**
   * @return true if the snapshot is reserved for the checkpoint, false if it is already deleted
   */
  boolean reserve(final long checkpointId, final FileBasedSnapshot snapshot) {
    final var reservation = snapshot.reserveOnActor();
    if (reservation == null) {
      return false;
    }
    reservations
        .computeIfAbsent(new Key(checkpointId, snapshot.getId()), key -> new Reservation(snapshot))
        .handles
        .push(reservation);
    return true;
  }

  Optional<PersistedSnapshot> get(final long checkpointId, final String snapshotId) {
    return Optional.ofNullable(reservations.get(new Key(checkpointId, snapshotId)))
        .map(reservation -> reservation.snapshot);
  }

  /** Releases one reservation of the snapshot for the checkpoint; no-op if there is none. */
  void release(final long checkpointId, final String snapshotId) {
    final var key = new Key(checkpointId, snapshotId);
    final var reservation = reservations.get(key);
    if (reservation == null) {
      return;
    }

    reservation.handles.pop().releaseOnActor();
    if (reservation.handles.isEmpty()) {
      reservations.remove(key);
    }
  }

  void releaseAll() {
    reservations.values().stream()
        .flatMap(reservation -> reservation.handles.stream())
        .forEach(FileBasedSnapshotReservation::releaseOnActor);
    reservations.clear();
  }

  private record Key(long checkpointId, String snapshotId) {}

  private static final class Reservation {
    private final FileBasedSnapshot snapshot;
    private final Deque<FileBasedSnapshotReservation> handles = new ArrayDeque<>();

    private Reservation(final FileBasedSnapshot snapshot) {
      this.snapshot = snapshot;
    }
  }
}
