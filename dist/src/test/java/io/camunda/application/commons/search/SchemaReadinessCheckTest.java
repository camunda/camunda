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

import io.camunda.cluster.SecondaryStorageAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class SchemaReadinessCheckTest {

  @Test
  void shouldBeUpWhenAtLeastOnePhysicalTenantIsAvailable() {
    // given
    final var availability = mock(SecondaryStorageAvailability.class);
    when(availability.anyAvailable()).thenReturn(true);
    final var readinessCheck = new SchemaReadinessCheck(availability);

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeDownWhenNoPhysicalTenantIsAvailable() {
    // given
    final var availability = mock(SecondaryStorageAvailability.class);
    when(availability.anyAvailable()).thenReturn(false);
    final var readinessCheck = new SchemaReadinessCheck(availability);

    // when
    final var health = readinessCheck.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }
}
