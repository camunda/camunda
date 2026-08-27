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

/**
 * Shared resilience4j {@link Retry} configuration for search-client calls made on the
 * authentication path, plus the classification of which failures are worth retrying. A
 * shard-availability blip on the underlying index is transient; a malformed request or a permission
 * problem is not, and retrying it would only delay a failure that a retry can't fix.
 */
public final class TransientSearchRetry {

  public static final int MAX_ATTEMPTS = 3;
  private static final long INITIAL_DELAY_MS = 100;

  private TransientSearchRetry() {}

  /** Creates a new {@link Retry} instance, retrying only on {@link #isTransient} failures. */
  public static Retry of(final String name) {
    return Retry.of(
        name,
        RetryConfig.custom()
            .maxAttempts(MAX_ATTEMPTS)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(INITIAL_DELAY_MS, 2))
            .retryOnException(TransientSearchRetry::isTransient)
            .build());
  }

  /**
   * @return {@code true} if {@code throwable} indicates a transient infrastructure problem (e.g. a
   *     connection drop or a search-server error such as a shard-availability outage) rather than a
   *     request or configuration problem that a retry cannot fix.
   */
  public static boolean isTransient(final Throwable throwable) {
    if (throwable instanceof final CamundaSearchException cse) {
      return switch (cse.getReason()) {
        case CONNECTION_FAILED, SEARCH_CLIENT_FAILED, SEARCH_SERVER_FAILED, UNKNOWN -> true;
        default -> false;
      };
    }
    return throwable instanceof RuntimeException;
  }
}
