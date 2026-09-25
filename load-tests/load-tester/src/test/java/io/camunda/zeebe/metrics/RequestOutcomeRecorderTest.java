/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.ProblemException;
import io.grpc.Status;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class RequestOutcomeRecorderTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final RequestOutcomeRecorder recorder = new RequestOutcomeRecorder(registry);

  @Test
  void shouldRecordSuccess() {
    // when
    recorder.record("create_instance", null);

    // then
    assertThat(count("create_instance", "ok", "none")).isEqualTo(1);
  }

  @Test
  void shouldRecordHttpStatusAndProblemTitle() {
    // given
    final var problem = new ProblemDetail().setStatus(503).setTitle("RESOURCE_EXHAUSTED");
    final var error =
        new CompletionException(new ProblemException(503, "Service Unavailable", problem));

    // when
    recorder.record("create_instance", error);

    // then
    assertThat(count("create_instance", "503", "RESOURCE_EXHAUSTED")).isEqualTo(1);
  }

  @Test
  void shouldRecordGrpcStatusCode() {
    // given
    final var error = new ExecutionException(new ClientStatusException(Status.UNAVAILABLE, null));

    // when
    recorder.record("complete_job", error);

    // then
    assertThat(count("complete_job", "UNAVAILABLE", "none")).isEqualTo(1);
  }

  @Test
  void shouldRecordCauseTypeWhenThereIsNoResponse() {
    // given
    final var error =
        new CompletionException(new ClientException("timed out", new TimeoutException()));

    // when
    recorder.record("create_instance", error);

    // then
    assertThat(count("create_instance", "TimeoutException", "none")).isEqualTo(1);
  }

  private double count(final String command, final String status, final String reason) {
    return registry
        .get(AppMetricsDoc.REQUESTS.getName())
        .tag("command", command)
        .tag("status", status)
        .tag("reason", reason)
        .counter()
        .count();
  }
}
