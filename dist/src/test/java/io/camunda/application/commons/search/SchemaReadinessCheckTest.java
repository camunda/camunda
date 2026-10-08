/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.pt.PhysicalTenantSchemaInitializationHealthIndicator;
import io.camunda.application.commons.pt.SchemaInitializationStatus;
import io.camunda.application.commons.pt.SchemaInitializationStatus.State;
import io.camunda.cluster.SecondaryStorageReadiness;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.springframework.boot.health.contributor.Status;

class SchemaReadinessCheckTest {

  @Test
  void shouldBeUpWhenEveryPhysicalTenantIsInitialized() {
    // given
    final var readinessCheck =
        readinessCheck(
            Map.of("default", status(State.INITIALIZED), "tenanta", status(State.INITIALIZED)));

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeDegradedOnceAnInitializedPhysicalTenantEntersRecovery() {
    // given
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.isRecovering("tenanta")).thenReturn(true);
    final var readinessCheck =
        new SchemaReadinessCheck(
            readiness,
            () ->
                Map.of("default", status(State.INITIALIZED), "tenanta", status(State.INITIALIZED)));

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus())
        .isEqualTo(PhysicalTenantSchemaInitializationHealthIndicator.DEGRADED);
  }

  @ParameterizedTest
  @EnumSource(
      value = State.class,
      mode = Mode.EXCLUDE,
      names = {"INITIALIZED"})
  void shouldBeDegradedWhenOnePhysicalTenantIsNotInitialized(final State otherTenantState) {
    // given
    final var readinessCheck =
        readinessCheck(
            Map.of("default", status(State.INITIALIZED), "tenanta", status(otherTenantState)));

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus())
        .isEqualTo(PhysicalTenantSchemaInitializationHealthIndicator.DEGRADED);
  }

  @ParameterizedTest
  @EnumSource(
      value = State.class,
      names = {"INITIALIZING", "RETRYING", "RECOVERING"})
  void shouldBeDegradedWhileNoPhysicalTenantIsInitializedButOneMayStillBe(final State state) {
    // given
    final var readinessCheck =
        readinessCheck(Map.of("default", status(state), "tenanta", status(State.FAILED)));

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus())
        .isEqualTo(PhysicalTenantSchemaInitializationHealthIndicator.DEGRADED);
  }

  @Test
  void shouldBeDownWhenEveryPhysicalTenantStoppedWithoutBeingInitialized() {
    // given
    final var readinessCheck =
        readinessCheck(Map.of("default", status(State.FAILED), "tenanta", status(State.GAVE_UP)));

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldBeUpWithoutSchemaInitializationStatusesWhenAPhysicalTenantIsReady() {
    // given
    final var readinessCheck = new SchemaReadinessCheck(readiness(true), Map::of);

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeDownWithoutSchemaInitializationStatusesWhenNoPhysicalTenantIsReady() {
    // given
    final var readinessCheck = new SchemaReadinessCheck(readiness(false), Map::of);

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  private static SchemaReadinessCheck readinessCheck(
      final Map<String, SchemaInitializationStatus> statuses) {
    return new SchemaReadinessCheck(mock(SecondaryStorageReadiness.class), () -> statuses);
  }

  private static SecondaryStorageReadiness readiness(final boolean anyReady) {
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.anyReady()).thenReturn(anyReady);
    return readiness;
  }

  private static SchemaInitializationStatus status(final State state) {
    return new SchemaInitializationStatus(state, 0, null);
  }
}
