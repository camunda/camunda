/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.metrics.AuthorizationMetricsDoc.AuthorizationOutcome;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationCheckMetricsTest {

  private SimpleMeterRegistry registry;
  private AuthorizationCheckMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new AuthorizationCheckMetrics(registry);
  }

  @Test
  void shouldRecordAuthorizedTimerWithCorrectTags() {
    // when
    metrics.record(
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ,
        AuthorizationOutcome.AUTHORIZED,
        1_000_000L);

    // then
    final var timer =
        registry
            .find("zeebe.authorization.check.latency")
            .tag("resourceType", "PROCESS_DEFINITION")
            .tag("permissionType", "READ")
            .tag("outcome", "authorized")
            .timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void shouldRecordDeniedTimerWithCorrectTags() {
    // when
    metrics.record(
        AuthorizationResourceType.DECISION_DEFINITION,
        PermissionType.CREATE,
        AuthorizationOutcome.DENIED,
        500_000L);

    // then
    final var timer =
        registry
            .find("zeebe.authorization.check.latency")
            .tag("resourceType", "DECISION_DEFINITION")
            .tag("permissionType", "CREATE")
            .tag("outcome", "denied")
            .timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void shouldAccumulateCountForSameTagCombination() {
    // when
    metrics.record(
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ,
        AuthorizationOutcome.AUTHORIZED,
        1_000_000L);
    metrics.record(
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ,
        AuthorizationOutcome.AUTHORIZED,
        2_000_000L);

    // then
    final var timer =
        registry
            .find("zeebe.authorization.check.latency")
            .tag("resourceType", "PROCESS_DEFINITION")
            .tag("permissionType", "READ")
            .tag("outcome", "authorized")
            .timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(2);
  }

  @Test
  void shouldKeepSeparateTimersForDifferentTagCombinations() {
    // when
    metrics.record(
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ,
        AuthorizationOutcome.AUTHORIZED,
        1_000_000L);
    metrics.record(
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ,
        AuthorizationOutcome.DENIED,
        1_000_000L);

    // then
    final var authorizedTimer =
        registry.find("zeebe.authorization.check.latency").tag("outcome", "authorized").timer();
    final var deniedTimer =
        registry.find("zeebe.authorization.check.latency").tag("outcome", "denied").timer();
    assertThat(authorizedTimer).isNotNull();
    assertThat(deniedTimer).isNotNull();
    assertThat(authorizedTimer.count()).isEqualTo(1);
    assertThat(deniedTimer.count()).isEqualTo(1);
  }
}
