/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Snapshot reservations made on behalf of checkpoints, counted per checkpoint and snapshot.
 * Requests for the same checkpoint share a reservation, so the snapshot is only released once each
 * of them released it.
 *
 * <p>Not thread-safe: must only be used on the snapshot store's actor.
 */
final class CheckpointReservations {

  private final Map<Key, Reservation> reservations = new HashMap<>();

  /**
   * @return true if the snapshot is reserved for the checkpoint, false if it is already deleted
   */
  boolean reserve(final long checkpointId, final FileBasedSnapshot snapshot) {
    final var key = new Key(checkpointId, snapshot.getId());
    final var existing = reservations.get(key);
    if (existing != null) {
      existing.count++;
      return true;
    }

    final var reservation = snapshot.reserveOnActor();
    if (reservation == null) {
      return false;
    }
    reservations.put(key, new Reservation(snapshot, reservation));
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

    reservation.count--;
    if (reservation.count == 0) {
      reservation.reservation.releaseOnActor();
      reservations.remove(key);
    }
  }

  void releaseAll() {
    reservations.values().forEach(reservation -> reservation.reservation.releaseOnActor());
    reservations.clear();
  }

  private record Key(long checkpointId, String snapshotId) {}

  private static final class Reservation {
    private final FileBasedSnapshot snapshot;
    private final FileBasedSnapshotReservation reservation;
    private int count = 1;

    private Reservation(
        final FileBasedSnapshot snapshot, final FileBasedSnapshotReservation reservation) {
      this.snapshot = snapshot;
      this.reservation = reservation;
    }
  }
}
