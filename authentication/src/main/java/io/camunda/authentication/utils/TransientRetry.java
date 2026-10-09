/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.utils;

import io.camunda.search.exception.CamundaSearchException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.jspecify.annotations.Nullable;

/**
 * Shared resilience4j {@link Retry} configuration for search-client calls made on the
 * authentication path, plus the classification of which failures are worth retrying.
 */
public final class TransientRetry {

  public static final int MAX_ATTEMPTS = 3;
  private static final long INITIAL_DELAY_MS = 100;

  private TransientRetry() {}

  /** Creates a new {@link Retry} instance, retrying only on {@link #isTransient} failures. */
  public static Retry of(final String name) {
    return Retry.of(
        name,
        RetryConfig.custom()
            .maxAttempts(MAX_ATTEMPTS)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(INITIAL_DELAY_MS, 2))
            .retryOnException(TransientRetry::isTransient)
            .build());
  }

  /**
   * @return {@code true} if {@code throwable} carries a {@link CamundaSearchException} reporting an
   *     infrastructure problem. Anything else is attempted once and never retried; what happens to
   *     it afterwards is the caller's decision.
   *     <p>{@link CamundaSearchException.Reason#UNKNOWN} is deliberately not transient: the search
   *     clients classify every real infrastructure failure explicitly, so UNKNOWN only ever reaches
   *     here from a deterministic error raised without a reason, which a retry cannot fix.
   */
  public static boolean isTransient(final Throwable throwable) {
    // Callers reaching the search layer through the *Services API only see the failure after
    // ErrorMapper rewrapped it as a ServiceException whose Status collapses several reasons onto
    // INTERNAL; the preserved cause is what still carries the precise reason.
    final var searchException = findSearchException(throwable);
    if (searchException == null) {
      return false;
    }
    return switch (searchException.getReason()) {
      case CONNECTION_FAILED, SEARCH_CLIENT_FAILED, SEARCH_SERVER_FAILED -> true;
      default -> false;
    };
  }

  private static @Nullable CamundaSearchException findSearchException(final Throwable throwable) {
    for (Throwable current = throwable;
        current != null && current != current.getCause();
        current = current.getCause()) {
      if (current instanceof final CamundaSearchException cse) {
        return cse;
      }
    }
    return null;
  }
}
