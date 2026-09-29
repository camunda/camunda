/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.zeebe.metrics.WorkerMetricsDoc;
import io.camunda.zeebe.metrics.WorkerMetricsDoc.WorkerMetricKeyNames;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseChecker extends Thread {
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Worker.class), Duration.ofSeconds(5));
  private static final int SC_TOO_MANY_REQUESTS = 429;
  private static final int SC_SERVICE_UNAVAILABLE = 503;

  private final BlockingQueue<PendingRequest> requests;
  private final Map<CompleteOutcome, Timer> completeDurationTimers =
      new EnumMap<>(CompleteOutcome.class);
  private volatile boolean shuttingDown = false;

  public ResponseChecker(
      final BlockingQueue<PendingRequest> requests, final MeterRegistry registry) {
    this.requests = requests;
    for (final var outcome : CompleteOutcome.values()) {
      completeDurationTimers.put(
          outcome,
          MicrometerUtil.buildTimer(WorkerMetricsDoc.COMPLETE_DURATION)
              .tag(WorkerMetricKeyNames.OUTCOME.asString(), outcome.tagValue)
              .register(registry));
    }
  }

  @Override
  public void run() {
    while (!shuttingDown) {
      try {
        final var request = requests.take();
        try {
          request.future().get();
          recordDuration(CompleteOutcome.SUCCESS, request);
        } catch (final ExecutionException e) {
          final var outcome = classifyFailure(e.getCause());
          recordDuration(outcome, request);
          if (outcome != CompleteOutcome.BACKPRESSURE) {
            // we don't want to flood the log
            THROTTLED_LOGGER.warn("Request failed", e);
          }
        }
      } catch (final InterruptedException e) {
        // ignore and retry
      }
    }
  }

  public void close() {
    shuttingDown = true;
    interrupt();
  }

  private void recordDuration(final CompleteOutcome outcome, final PendingRequest request) {
    completeDurationTimers
        .get(outcome)
        .record(System.nanoTime() - request.startNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Backpressure is the expected steady-state rejection under load and is reported separately
   * rather than logged. It reaches the client differently per transport — as {@code
   * RESOURCE_EXHAUSTED} over gRPC and as HTTP 429/503 over REST — and only the gRPC form used to be
   * recognised here, so every REST failure went entirely unreported.
   */
  private static CompleteOutcome classifyFailure(final Throwable cause) {
    if (cause instanceof StatusRuntimeException) {
      return ((StatusRuntimeException) cause).getStatus().getCode() == Code.RESOURCE_EXHAUSTED
          ? CompleteOutcome.BACKPRESSURE
          : CompleteOutcome.ERROR;
    }
    if (cause instanceof ClientHttpException) {
      final int code = ((ClientHttpException) cause).code();
      return code == SC_TOO_MANY_REQUESTS || code == SC_SERVICE_UNAVAILABLE
          ? CompleteOutcome.BACKPRESSURE
          : CompleteOutcome.ERROR;
    }
    return CompleteOutcome.ERROR;
  }

  /**
   * A dispatched job completion together with the {@link System#nanoTime()} reading taken just
   * before it was sent, so its round-trip can be timed once the response is checked here.
   */
  public record PendingRequest(Future<?> future, long startNanos) {}

  private enum CompleteOutcome {
    SUCCESS("success"),
    BACKPRESSURE("backpressure"),
    ERROR("error");

    private final String tagValue;

    CompleteOutcome(final String tagValue) {
      this.tagValue = tagValue;
    }
  }
}
