/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter.Type;
import org.junit.jupiter.api.Test;

class WorkerMetricsDocTest {

  @Test
  void shouldDefineMessagePublicationsCounter() {
    // given
    final var metric = WorkerMetricsDoc.MESSAGE_PUBLICATIONS;

    // when / then
    assertThat(metric.getName()).isEqualTo("worker.message.publications");
    assertThat(metric.getType()).isEqualTo(Type.COUNTER);
    assertThat(metric.getDescription())
        .isEqualTo(
            "Total number of message publications by the worker, by outcome and error type.");
    assertThat(metric.getKeyNames())
        .containsExactly(ResultMetricKeyNames.OUTCOME, ResultMetricKeyNames.ERROR);
  }

  @Test
  void shouldDefineJobCompletionsCounter() {
    // given
    final var metric = WorkerMetricsDoc.JOB_COMPLETIONS;

    // when / then
    assertThat(metric.getName()).isEqualTo("worker.job.completions");
    assertThat(metric.getType()).isEqualTo(Type.COUNTER);
    assertThat(metric.getDescription())
        .isEqualTo("Total number of answered job completion requests by outcome and error type.");
    assertThat(metric.getKeyNames())
        .containsExactly(ResultMetricKeyNames.OUTCOME, ResultMetricKeyNames.ERROR);
  }
}
