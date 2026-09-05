/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.zeebe.worker.ResponseChecker.PendingRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResponseCheckerTest {

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final LinkedBlockingQueue<PendingRequest> requests = new LinkedBlockingQueue<>();
  private final ResponseChecker responseChecker = new ResponseChecker(requests, registry);

  @AfterEach
  void tearDown() {
    responseChecker.close();
  }

  @Test
  void shouldRecordCompleteDurationOfSuccessfulCompletion() {
    // given
    responseChecker.start();
    final long startNanos = System.nanoTime() - Duration.ofMillis(20).toNanos();

    // when
    requests.add(new PendingRequest(CompletableFuture.completedFuture("ok"), startNanos));

    // then — the round-trip is measured from the timestamp taken before the command was sent
    awaitCount("success", 1);
    assertThat(timer("success").totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(20);
  }

  @Test
  void shouldTagBackpressureSeparatelyPerTransport() {
    // given — backpressure surfaces as RESOURCE_EXHAUSTED over gRPC and as HTTP 429/503 over REST
    responseChecker.start();

    // when
    requests.add(failedRequest(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED)));
    requests.add(failedRequest(new ClientHttpException(429, "Too Many Requests")));
    requests.add(failedRequest(new ClientHttpException(503, "Service Unavailable")));

    // then
    awaitCount("backpressure", 3);
    assertThat(timer("error").count()).isZero();
  }

  @Test
  void shouldTagOtherFailuresAsError() {
    // given
    responseChecker.start();

    // when — a non-backpressure status on either transport, and a failure with neither
    requests.add(failedRequest(new StatusRuntimeException(Status.UNAVAILABLE)));
    requests.add(failedRequest(new ClientHttpException(500, "Internal Server Error")));
    requests.add(failedRequest(new RuntimeException("boom")));

    // then
    awaitCount("error", 3);
    assertThat(timer("backpressure").count()).isZero();
  }

  private static PendingRequest failedRequest(final Throwable cause) {
    return new PendingRequest(CompletableFuture.failedFuture(cause), System.nanoTime());
  }

  private void awaitCount(final String outcome, final long expected) {
    await().untilAsserted(() -> assertThat(timer(outcome).count()).isEqualTo(expected));
  }

  private Timer timer(final String outcome) {
    return registry.get("worker.complete.duration").tag("outcome", outcome).timer();
  }
}
