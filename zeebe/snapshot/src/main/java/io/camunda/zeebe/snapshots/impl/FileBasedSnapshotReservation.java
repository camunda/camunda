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

public final class FileBasedSnapshotReservation implements PersistedSnapshotReservation {

  private static final byte IN_MEMORY_RESERVATION_ID = -1;

  private final FileBasedSnapshotReservations reservations;
  private final byte reservationId;

  private FileBasedSnapshotReservation(
      final FileBasedSnapshotReservations reservations, final byte reservationId) {
    this.reservations = reservations;
    this.reservationId = reservationId;
  }

  public static FileBasedSnapshotReservation inMemory(
      final FileBasedSnapshotReservations reservations) {
    return new FileBasedSnapshotReservation(reservations, IN_MEMORY_RESERVATION_ID);
  }

  public static FileBasedSnapshotReservation persisted(
      final FileBasedSnapshotReservations reservations, final byte reservationId) {
    if (reservationId == IN_MEMORY_RESERVATION_ID) {
      throw new IllegalArgumentException(
          "reservationId cannot be equal to " + IN_MEMORY_RESERVATION_ID);
    }
    return new FileBasedSnapshotReservation(reservations, reservationId);
  }

  @Override
  public byte reservationId() {
    return reservationId;
  }

  public boolean isInMemory() {
    return reservationId == IN_MEMORY_RESERVATION_ID;
  }

  @Override
  public ActorFuture<Void> release() {
    return reservations.removeReservation(this);
  }
}
