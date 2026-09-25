/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static io.camunda.application.commons.pt.PhysicalTenantSchemaInitializationHealthIndicator.DEGRADED;
import static io.camunda.application.commons.pt.PhysicalTenantSchemaInitializationHealthIndicator.MAX_ERROR_LENGTH;
import static io.camunda.application.commons.pt.PhysicalTenantSchemaInitializationHealthIndicator.TRUNCATION_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.pt.SchemaInitializationStatus.State;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

final class PhysicalTenantSchemaInitializationHealthIndicatorTest {

  private static final SchemaInitializationStatus INITIALIZED =
      new SchemaInitializationStatus(State.INITIALIZED, 0, null);

  @Test
  void shouldBeUpWhenEveryTenantIsInitialized() {
    // when
    final var health = healthOf(Map.of("a", INITIALIZED, "b", INITIALIZED));

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("a", Map.of("status", "UP", "state", "INITIALIZED"))
        .containsEntry("b", Map.of("status", "UP", "state", "INITIALIZED"));
  }

  @Test
  void shouldStayDegradedWhenOneTenantFailedWhileAnotherIsServed() {
    // given
    final var failed =
        new SchemaInitializationStatus(
            State.FAILED, 1, new IllegalStateException("schema version is incompatible"));

    // when
    final var health = healthOf(ordered("a", INITIALIZED, "b", failed));

    // then - DEGRADED answers 200: a node still serving tenant a must not read as down, or a
    // health check wired to this endpoint restarts it into the same failure of tenant b
    assertThat(health.getStatus()).isEqualTo(DEGRADED);
    assertThat(health.getDetails())
        .containsEntry(
            "b",
            Map.of(
                "status",
                "DOWN",
                "state",
                "FAILED",
                "failedAttempts",
                1,
                "error",
                "java.lang.IllegalStateException: schema version is incompatible"));
  }

  @Test
  void shouldBeDownOnceEveryTenantStoppedWithoutSuccess() {
    // given
    final var failure = new IllegalStateException("boom");

    // when
    final var health =
        healthOf(
            ordered(
                "a",
                new SchemaInitializationStatus(State.FAILED, 1, failure),
                "b",
                new SchemaInitializationStatus(State.GAVE_UP, 3, failure)));

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldBeDegradedWhileEveryTenantIsStillRetrying() {
    // given
    final var retrying =
        new SchemaInitializationStatus(State.RETRYING, 2, new IllegalStateException("boom"));

    // when
    final var health = healthOf(ordered("a", retrying, "b", retrying));

    // then - nothing has failed for good, so the node is not down however little it serves
    assertThat(health.getStatus()).isEqualTo(DEGRADED);
  }

  @Test
  void shouldCutALongErrorSoTheEndpointStaysReadable() {
    // given - a failure as long as a mapping-validation one listing every differing field
    final var failure = new IllegalStateException("x".repeat(10_000));

    // when
    final var health =
        healthOf(Map.of("a", new SchemaInitializationStatus(State.FAILED, 1, failure)));

    // then
    @SuppressWarnings("unchecked")
    final var tenant = (Map<String, Object>) health.getDetails().get("a");
    assertThat((String) tenant.get("error"))
        .hasSize(MAX_ERROR_LENGTH)
        .startsWith("java.lang.IllegalStateException: xxx")
        .endsWith(TRUNCATION_SUFFIX);
  }

  @Test
  void shouldNotReportPastFailuresOfAnInitializedTenant() {
    // given - the tenant failed twice before it was initialized
    final var recovered =
        new SchemaInitializationStatus(State.INITIALIZED, 2, new IllegalStateException("boom"));

    // when
    final var health = healthOf(Map.of("a", recovered));

    // then
    assertThat(health.getDetails())
        .containsEntry("a", Map.of("status", "UP", "state", "INITIALIZED"));
  }

  @ParameterizedTest
  @CsvSource({
    "INITIALIZED, UP",
    "INITIALIZING, DEGRADED",
    "RETRYING, DEGRADED",
    "RECOVERING, DEGRADED",
    "FAILED, DOWN",
    "GAVE_UP, DOWN",
    "ABORTED, DOWN"
  })
  void shouldReportEachStateAsTheTenantsStatus(final State state, final String expectedStatus) {
    // when
    final var health = healthOf(Map.of("a", new SchemaInitializationStatus(state, 0, null)));

    // then - a single tenant's status is the indicator's own
    assertThat(health.getStatus().getCode()).isEqualTo(expectedStatus);
    assertThat(health.getDetails())
        .containsEntry("a", Map.of("status", expectedStatus, "state", state.name()));
  }

  private static Health healthOf(final Map<String, SchemaInitializationStatus> statuses) {
    return new PhysicalTenantSchemaInitializationHealthIndicator(() -> statuses).health();
  }

  private static Map<String, SchemaInitializationStatus> ordered(
      final String firstTenant,
      final SchemaInitializationStatus first,
      final String secondTenant,
      final SchemaInitializationStatus second) {
    final var statuses = new LinkedHashMap<String, SchemaInitializationStatus>();
    statuses.put(firstTenant, first);
    statuses.put(secondTenant, second);
    return statuses;
  }
}
