/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HealthZeebeClientPropertiesTest {

  @Test
  void shouldRejectZeroDurationIn() {
    final HealthZeebeClientProperties healthZeebeClientProperties =
        new HealthZeebeClientProperties();
    assertThatThrownBy(() -> healthZeebeClientProperties.setRequestTimeout(Duration.ZERO))
        .isExactlyInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNegativeDurationIn() {
    final HealthZeebeClientProperties healthZeebeClientProperties =
        new HealthZeebeClientProperties();
    assertThatThrownBy(() -> healthZeebeClientProperties.setRequestTimeout(Duration.ofSeconds(-10)))
        .isExactlyInstanceOf(IllegalArgumentException.class);
  }
}
