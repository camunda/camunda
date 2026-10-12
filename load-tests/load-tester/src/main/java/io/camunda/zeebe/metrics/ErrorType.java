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
import io.grpc.StatusRuntimeException;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Classifies request failures into a small set of values that are safe to use as metric tags. */
public final class ErrorType {

  private ErrorType() {}

  /** Returns whether the cluster rejected the request because it is under backpressure. */
  public static boolean isBackpressure(final Throwable error) {
    return switch (of(error)) {
      case "grpc_resource_exhausted", "http_429" -> true;
      default -> false;
    };
  }

  /**
   * Returns the gRPC status code, the HTTP status code or the exception class of the failure,
   * looking through {@link CompletionException} and {@link ExecutionException} wrappers.
   */
  public static String of(final Throwable error) {
    Throwable cause = error;
    while ((cause instanceof CompletionException || cause instanceof ExecutionException)
        && cause.getCause() != null) {
      cause = cause.getCause();
    }

    if (cause instanceof final ClientStatusException statusException) {
      return "grpc_" + statusException.getStatusCode().name().toLowerCase(Locale.ROOT);
    } else if (cause instanceof final StatusRuntimeException statusException) {
      return "grpc_" + statusException.getStatus().getCode().name().toLowerCase(Locale.ROOT);
    } else if (cause instanceof final ClientHttpException httpException) {
      return "http_" + httpException.code();
    }
    return cause.getClass().getSimpleName();
  }
}
