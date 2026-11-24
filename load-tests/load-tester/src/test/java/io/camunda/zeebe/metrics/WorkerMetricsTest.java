/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class WorkerMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final WorkerMetrics metrics = new WorkerMetrics(registry);

  @Test
  void shouldRecordSuccessfulCompletion() {
    // given
    final long start = System.nanoTime();

    // when
    metrics.recordCompletion(start, null);

    // then
    assertThat(
            registry
                .get("worker.job.completion")
                .tag("outcome", "success")
                .tag("error", "none")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void shouldRecordFailedMessagePublishWithErrorType() {
    // given
    final long start = System.nanoTime();

    // when
    metrics.recordMessagePublish(start, new IllegalStateException("boom"));

    // then
    assertThat(
            registry
                .get("worker.message.publish")
                .tag("outcome", "failure")
                .tag("error", "IllegalStateException")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void shouldCountDroppedCompletionTracking() {
    // when
    metrics.recordCompletionTrackingDropped();
    metrics.recordCompletionTrackingDropped();

    // then
    assertThat(registry.get("worker.job.completion.tracking.dropped").counter().count())
        .isEqualTo(2);
  }
}
