/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import static io.camunda.zeebe.util.health.HealthStatus.DEAD;
import static io.camunda.zeebe.util.health.HealthStatus.HEALTHY;
import static io.camunda.zeebe.util.health.HealthStatus.UNHEALTHY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class HealthStatusTest {

  @ParameterizedTest
  @EnumSource(HealthStatus.class)
  public void shouldReturnItselfWhenEqual(final HealthStatus status) {
    assertThat(status.combine(status)).isEqualTo(status);
  }

  @ParameterizedTest
  @EnumSource(HealthStatus.class)
  public void shouldReturnDeadWhenOneIsDead(final HealthStatus status) {
    assertThat(status.combine(DEAD)).isEqualTo(DEAD);
  }

  @ParameterizedTest
  @EnumSource(value = HealthStatus.class, names = "DEAD", mode = Mode.EXCLUDE)
  public void shouldReturnUnhealthyWhenUnhealthy(final HealthStatus status) {
    assertThat(UNHEALTHY.combine(status)).isEqualTo(UNHEALTHY);
  }

  @ParameterizedTest
  @EnumSource(value = HealthStatus.class, names = "HEALTHY", mode = Mode.EXCLUDE)
  public void shouldNotReturnHealthyIsOneIsNotHealthy(final HealthStatus status) {
    assertThat(HEALTHY.combine(status)).isNotEqualTo(HEALTHY);
  }

  @Test
  public void shouldSortBasedOnSeverity() {
    final var statuses = new HealthStatus[] {DEAD, HEALTHY, UNHEALTHY};
    Arrays.sort(statuses, HealthStatus.COMPARATOR);
    assertThat(statuses).containsExactly(HEALTHY, UNHEALTHY, DEAD);
  }
}
