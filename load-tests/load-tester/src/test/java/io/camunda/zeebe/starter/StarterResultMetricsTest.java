/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.grpc.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class StarterResultMetricsTest {

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final StarterResultMetrics metrics = new StarterResultMetrics(registry);

  @Test
  void shouldCountSuccess() {
    // when
    metrics.record(null);

    // then
    assertThat(count("success", "none")).isEqualTo(1.0);
  }

  @Test
  void shouldCountGrpcFailureByStatusCode() {
    // when
    metrics.record(
        new ClientStatusException(Status.RESOURCE_EXHAUSTED, new RuntimeException("backpressure")));

    // then
    assertThat(count("failure", "grpc_resource_exhausted")).isEqualTo(1.0);
    assertThat(count("success", "none")).isZero();
  }

  @Test
  void shouldCountRestFailureByHttpStatus() {
    // when
    metrics.record(new ClientHttpException(503, "Service Unavailable"));

    // then
    assertThat(count("failure", "http_503")).isEqualTo(1.0);
    assertThat(count("success", "none")).isZero();
  }

  @Test
  void shouldUnwrapCompletionExceptionForErrorType() {
    // when
    metrics.record(new CompletionException(new ClientHttpException(429, "Too Many Requests")));

    // then
    assertThat(count("failure", "http_429")).isEqualTo(1.0);
  }

  @Test
  void shouldUseExceptionClassForOtherFailures() {
    // when
    metrics.record(new IllegalStateException("boom"));

    // then
    assertThat(count("failure", "IllegalStateException")).isEqualTo(1.0);
  }

  private double count(final String outcome, final String error) {
    final var counter =
        registry
            .find(StarterMetricsDoc.PROCESS_INSTANCES_STARTED.getName())
            .tag("outcome", outcome)
            .tag("error", error)
            .counter();
    return counter == null ? 0.0 : counter.count();
  }
}
