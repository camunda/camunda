/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.metrics.WorkerMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkerResultMetricsTest {

  private static final String JOB_TYPE = "benchmark-task";

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final List<Object> pending = new ArrayList<>();
  private final WorkerResultMetrics metrics = new WorkerResultMetrics(registry, JOB_TYPE, pending);

  @Test
  void shouldTagEveryMetricWithTheJobType() {
    // given / when
    metrics.recordMessagePublication();
    metrics.recordMessagePublication(new IllegalStateException("failed"));
    metrics.recordJobCompletion();
    metrics.recordJobCompletion(new IllegalStateException("failed"));

    // then
    assertThat(registry.getMeters())
        .isNotEmpty()
        .allSatisfy(meter -> assertThat(meter.getId().getTag("job_type")).isEqualTo(JOB_TYPE));
  }

  @Test
  void shouldCountDroppedTracking() {
    // given
    final var name = WorkerMetricsDoc.JOB_COMPLETION_TRACKING_DROPPED.getName();

    // when
    metrics.recordTrackingDropped();
    metrics.recordTrackingDropped();

    // then
    assertThat(registry.get(name).counter().count()).isEqualTo(2.0);
  }

  @Test
  void shouldReportPendingCompletions() {
    // given
    final var name = WorkerMetricsDoc.JOB_COMPLETIONS_PENDING.getName();

    // when
    pending.add(new Object());
    pending.add(new Object());

    // then
    assertThat(registry.get(name).gauge().value()).isEqualTo(2.0);
  }

  @Test
  void shouldNotTagSuccessWithError() {
    // given
    final var prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    final var prometheusMetrics = new WorkerResultMetrics(prometheus, JOB_TYPE, pending);

    // when
    prometheusMetrics.recordJobCompletion();
    prometheusMetrics.recordJobCompletion(new IllegalStateException("failed"));

    // then
    assertThat(prometheus.scrape())
        .contains(
            "worker_job_completions_total{job_type=\"benchmark-task\",outcome=\"success\"} 1.0")
        .contains(
            "worker_job_completions_total{error=\"IllegalStateException\",job_type=\"benchmark-task\",outcome=\"failure\"} 1.0");
  }
}
