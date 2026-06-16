/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationCheckMetricsTest {

  private SimpleMeterRegistry registry;
  private AuthorizationCheckMetrics metrics;

  @Mock Timer mockTimer;

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
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(1_000_000.0);
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

  @Test
  void shouldSwallowRuntimeExceptionFromBrokenTimer() {
    // given
    doThrow(new RuntimeException("broken registry")).when(mockTimer).record(anyLong(), any());
    final var brokenMetrics = new AuthorizationCheckMetrics(mockTimer);

    // when / then
    assertThatNoException().isThrownBy(() -> brokenMetrics.record(1_000_000L));
  }
}
