/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import java.time.Duration;
import java.util.Set;

/**
 * Evaluates retry policy for batch operation failures.
 *
 * <p>This policy implements exponential backoff retry strategy with configurable parameters for
 * initial delay, maximum delay, maximum retries, and backoff factor. It determines whether a
 * failure should be retried or should fail immediately.
 *
 * <p>The policy will immediately fail operations that encounter exceptions with specific reasons
 * such as NOT_FOUND, NOT_UNIQUE, SECONDARY_STORAGE_NOT_SET, or FORBIDDEN.
 *
 * @see RetryDecision
 */
public class BatchOperationRetryPolicy {
  private static final Set<Reason> FAIL_IMMEDIATELY_REASONS =
      Set.of(
          Reason.NOT_FOUND, Reason.NOT_UNIQUE, Reason.SECONDARY_STORAGE_NOT_SET, Reason.FORBIDDEN);

  private final Duration initialRetryDelay;
  private final Duration maxRetryDelay;
  private final int maxRetries;
  private final int backoffFactor;

  public BatchOperationRetryPolicy(
      final Duration initialRetryDelay,
      final Duration maxRetryDelay,
      final int maxRetries,
      final int backoffFactor) {
    this.initialRetryDelay = initialRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
    this.maxRetries = maxRetries;
    this.backoffFactor = backoffFactor;
  }

  /**
   * Evaluates whether a transient failure should be retried or should fail permanently.
   *
   * @param cursor the cursor to resume from if retrying
   * @param cause the exception that caused the failure
   * @param numAttempts the current number of attempts made
   * @return a decision indicating whether to retry (with delay) or fail permanently
   */
  public RetryDecision evaluate(final String cursor, final Throwable cause, final int numAttempts) {
    if (shouldFailImmediately(cause) || numAttempts >= maxRetries) {
      return RetryDecision.fail(
          formatErrorMessage(cursor, cause), BatchOperationErrorType.QUERY_FAILED);
    }
    final Duration nextDelay = calculateNextDelay(numAttempts);
    return RetryDecision.retry(nextDelay, numAttempts + 1, cursor);
  }

  private String formatErrorMessage(final String cursor, final Throwable cause) {
    return String.format(
        "Failed to initialize batch operation with end cursor: %s. Reason: %s",
        cursor, cause.getMessage());
  }

  private boolean shouldFailImmediately(final Throwable cause) {
    return cause instanceof final CamundaSearchException searchException
        && FAIL_IMMEDIATELY_REASONS.contains(searchException.getReason());
  }

  private Duration calculateNextDelay(final int attemptNumber) {
    // Use iterative multiplication with early-cap to avoid overflow from Math.pow
    Duration delay = initialRetryDelay;
    for (int i = 0; i < attemptNumber; i++) {
      delay = delay.multipliedBy(backoffFactor);
      if (delay.compareTo(maxRetryDelay) >= 0) {
        return maxRetryDelay;
      }
    }
    return delay;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  /** Represents the decision of whether to retry or fail permanently. */
  public sealed interface RetryDecision {
    static Fail fail(final String message, final BatchOperationErrorType errorType) {
      return new Fail(message, errorType);
    }

    static Retry retry(final Duration delay, final int numAttempts, final String cursor) {
      return new Retry(delay, numAttempts, cursor);
    }

    record Fail(String message, BatchOperationErrorType errorType) implements RetryDecision {}

    record Retry(Duration delay, int numAttempts, String cursor) implements RetryDecision {}
  }
}
