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
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.cluster.SecondaryStorageReadiness.NodeReadiness;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class SchemaReadinessCheckTest {

  @Test
  void shouldBeUpWhenTheNodeIsReady() {
    // given
    final var readinessCheck = readinessCheck(NodeReadiness.READY);

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeDegradedWhenTheNodeIsDegraded() {
    // given
    final var readinessCheck = readinessCheck(NodeReadiness.DEGRADED);

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus())
        .isEqualTo(PhysicalTenantSchemaInitializationHealthIndicator.DEGRADED);
  }

  @Test
  void shouldBeDownWhenTheNodeIsNotReady() {
    // given
    final var readinessCheck = readinessCheck(NodeReadiness.DOWN);

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  private static SchemaReadinessCheck readinessCheck(final NodeReadiness nodeReadiness) {
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.nodeReadiness()).thenReturn(nodeReadiness);
    return new SchemaReadinessCheck(readiness);
  }
}
