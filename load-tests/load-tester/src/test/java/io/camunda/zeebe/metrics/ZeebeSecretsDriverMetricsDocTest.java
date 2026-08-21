/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.metrics.ZeebeSecretsDriverMetricsDoc.ZeebeSecretsDriverMetricKeyNames;
import io.micrometer.core.instrument.Meter.Type;
import org.junit.jupiter.api.Test;

class ZeebeSecretsDriverMetricsDocTest {

  @Test
  void shouldDefineRequestLatencyTimer() {
    // given
    final var metric = ZeebeSecretsDriverMetricsDoc.REQUEST_LATENCY;

    // when / then
    assertThat(metric.getName()).isEqualTo("zeebe.secrets.request.latency");
    assertThat(metric.getType()).isEqualTo(Type.TIMER);
    assertThat(metric.getKeyNames())
        .containsExactly(
            ZeebeSecretsDriverMetricKeyNames.ENDPOINT, ZeebeSecretsDriverMetricKeyNames.OUTCOME);
    assertThat(metric.getTimerSLOs()).isNotEmpty();
  }

  @Test
  void shouldDefineRequestsSubmittedCounter() {
    // given
    final var metric = ZeebeSecretsDriverMetricsDoc.REQUESTS_SUBMITTED;

    // when / then
    assertThat(metric.getName()).isEqualTo("zeebe.secrets.requests.submitted");
    assertThat(metric.getType()).isEqualTo(Type.COUNTER);
    assertThat(metric.getKeyNames()).containsExactly(ZeebeSecretsDriverMetricKeyNames.ENDPOINT);
  }

  @Test
  void shouldDefineRunFinishedGauge() {
    // given
    final var metric = ZeebeSecretsDriverMetricsDoc.RUN_FINISHED;

    // when / then
    assertThat(metric.getName()).isEqualTo("zeebe.secrets.run.finished");
    assertThat(metric.getType()).isEqualTo(Type.GAUGE);
  }
}
