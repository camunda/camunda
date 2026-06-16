/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

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
  void shouldRecordTimer() {
    // when
    metrics.record(1_000_000L);

    // then
    final var timer = registry.find("zeebe.authorization.check.latency").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void shouldAccumulateCount() {
    // when
    metrics.record(1_000_000L);
    metrics.record(2_000_000L);

    // then
    final var timer = registry.find("zeebe.authorization.check.latency").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(2);
  }
}
