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
 * Snapshot reservations made on behalf of checkpoints, kept per checkpoint and snapshot. Requests
 * for the same checkpoint share a reservation, which the first release drops: a backup holds its
 * own reservation of the snapshot it uses, so it does not depend on this one once it started.
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
    if (reservations.containsKey(key)) {
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
        .map(Reservation::snapshot);
  }

  /** Releases the reservation of the snapshot for the checkpoint; no-op if there is none. */
  void release(final long checkpointId, final String snapshotId) {
    final var reservation = reservations.remove(new Key(checkpointId, snapshotId));
    if (reservation != null) {
      reservation.reservation().releaseOnActor();
    }
  }

  void releaseAll() {
    reservations.values().forEach(reservation -> reservation.reservation().releaseOnActor());
    reservations.clear();
  }

  private record Key(long checkpointId, String snapshotId) {}

  private record Reservation(
      FileBasedSnapshot snapshot, FileBasedSnapshotReservation reservation) {}
}
