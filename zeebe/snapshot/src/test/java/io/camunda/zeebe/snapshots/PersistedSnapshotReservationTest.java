/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.snapshots.PersistedSnapshotReservation.Reason;
import io.camunda.zeebe.snapshots.sbe.ReservationReason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class PersistedSnapshotReservationTest {
  @ParameterizedTest
  @EnumSource(PersistedSnapshotReservation.Reason.class)
  void shouldBeConvertedToSBEWithId(final Reason reason) {
    final var sbe = ReservationReason.get(reason.code());
    if (reason.code() < 255) {
      assertThat(sbe.name()).isEqualTo(reason.name());
    } else {
      assertThat(sbe.name()).isEqualTo("NULL_VAL");
    }
  }

  @ParameterizedTest
  @EnumSource(ReservationReason.class)
  void shouldBeConvertedToSBEWithId(final ReservationReason reservationReason) {
    final var reason = Reason.fromCode(reservationReason.value());
    if (reason.code() < 255) {
      assertThat(reason.name()).isEqualTo(reservationReason.name());
    } else {
      assertThat(reason).isEqualTo(Reason.UNKNOWN);
    }
  }
}
