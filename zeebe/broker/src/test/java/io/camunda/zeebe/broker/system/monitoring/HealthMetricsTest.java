/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

final class HealthMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final HealthMetrics healthMetrics = new HealthMetrics(registry);

  @Test
  void shouldReportRecoveringAsTwo() {
    // when
    healthMetrics.setRecovering();

    // then
    assertThat(registry.get("zeebe.health").gauge().value()).isEqualTo(2);
  }
}
