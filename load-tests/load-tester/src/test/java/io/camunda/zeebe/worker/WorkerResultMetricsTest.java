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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkerResultMetricsTest {

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final List<Object> pending = new ArrayList<>();
  private final WorkerResultMetrics metrics = new WorkerResultMetrics(registry, pending);

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
}
