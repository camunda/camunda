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

public class HealthStatusTest {
  @Test
  public void combineCorrectly() {
    for (final var status : HealthStatus.values()) {
      assertThat(status.combine(status)).isEqualTo(status);
    }

    assertThat(HEALTHY.combine(DEAD)).isEqualTo(DEAD);
    assertThat(DEAD.combine(HEALTHY)).isEqualTo(DEAD);

    assertThat(HEALTHY.combine(UNHEALTHY)).isEqualTo(UNHEALTHY);
    assertThat(UNHEALTHY.combine(HEALTHY)).isEqualTo(UNHEALTHY);

    assertThat(DEAD.combine(UNHEALTHY)).isEqualTo(DEAD);
    assertThat(UNHEALTHY.combine(DEAD)).isEqualTo(DEAD);
  }

  @Test
  public void sortCorrectly() {
    final var statuses = new HealthStatus[] {DEAD, HEALTHY, UNHEALTHY};
    Arrays.sort(statuses, HealthStatus.COMPARATOR);
    assertThat(statuses).containsExactly(HEALTHY, UNHEALTHY, DEAD);
  }
}
