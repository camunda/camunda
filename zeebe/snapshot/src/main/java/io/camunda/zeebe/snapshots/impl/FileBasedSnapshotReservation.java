/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshotReservation;
import java.util.UUID;

public record FileBasedSnapshotReservation(
    FileBasedSnapshotReservationStore reservations,
    UUID reservationId,
    boolean isInMemory,
    long validUntil,
    Reason reason)
    implements PersistedSnapshotReservation {

  public static FileBasedSnapshotReservation inMemory(
      final FileBasedSnapshotReservationStore reservations) {
    return new FileBasedSnapshotReservation(reservations, UUID.randomUUID(), true, 0L, null);
  }

  public static FileBasedSnapshotReservation persisted(
      final FileBasedSnapshotReservationStore reservations,
      final UUID reservationId,
      final long validUntil,
      final Reason reason) {
    return new FileBasedSnapshotReservation(reservations, reservationId, false, validUntil, reason);
  }

  @Override
  public ActorFuture<Void> release() {
    return reservations.removeReservation(this);
  }
}
