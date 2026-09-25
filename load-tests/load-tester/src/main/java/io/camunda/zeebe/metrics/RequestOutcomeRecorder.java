/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.metrics.AppMetricsDoc.RequestKeyNames;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Records the outcome of each client request in {@link AppMetricsDoc#REQUESTS}. */
public final class RequestOutcomeRecorder {

  static final String OK = "ok";
  static final String NONE = "none";

  private final MeterRegistry registry;

  public RequestOutcomeRecorder(final MeterRegistry registry) {
    this.registry = registry;
  }

  public void record(final String command, final Throwable error) {
    final String status;
    final String reason;
    if (error == null) {
      status = OK;
      reason = NONE;
    } else {
      final Throwable cause = unwrap(error);
      status = status(cause);
      reason = reason(cause);
    }

    Counter.builder(AppMetricsDoc.REQUESTS.getName())
        .description(AppMetricsDoc.REQUESTS.getDescription())
        .tag(RequestKeyNames.COMMAND.asString(), command)
        .tag(RequestKeyNames.STATUS.asString(), status)
        .tag(RequestKeyNames.REASON.asString(), reason)
        .register(registry)
        .increment();
  }

  private static Throwable unwrap(final Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static String status(final Throwable cause) {
    return switch (cause) {
      case final ClientHttpException e -> String.valueOf(e.code());
      case final ClientStatusException e -> e.getStatusCode().name();
      case final StatusRuntimeException e -> e.getStatus().getCode().name();
      default ->
          cause.getCause() != null
              ? cause.getCause().getClass().getSimpleName()
              : cause.getClass().getSimpleName();
    };
  }

  private static String reason(final Throwable cause) {
    if (cause instanceof final ProblemException e
        && e.details() != null
        && e.details().getTitle() != null) {
      return e.details().getTitle();
    }
    return NONE;
  }
}
