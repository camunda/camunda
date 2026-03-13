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
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.BatchOperationInitializationException;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.Failed;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.NeedsRetry;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.Success;
import java.time.Duration;
import java.util.Set;

/**
 * Handles retry logic for batch operations with configurable retry policies.
 *
 * <p>This handler implements exponential backoff retry strategy with configurable parameters for
 * initial delay, maximum delay, maximum retries, and backoff factor. It also supports immediate
 * failure for certain types of exceptions that should not be retried.
 *
 * <p>The handler will immediately fail operations that encounter exceptions with specific reasons
 * such as NOT_FOUND, NOT_UNIQUE, SECONDARY_STORAGE_NOT_SET, or FORBIDDEN.
 *
 * @see RetryableOperation
 * @see RetryResult
 */
public class BatchOperationRetryHandler {
  private static final Set<Reason> FAIL_IMMEDIATELY_REASONS =
      Set.of(
          Reason.NOT_FOUND, Reason.NOT_UNIQUE, Reason.SECONDARY_STORAGE_NOT_SET, Reason.FORBIDDEN);

  private final Duration initialRetryDelay;
  private final Duration maxRetryDelay;
  private final int maxRetries;
  private final int backoffFactor;

  public BatchOperationRetryHandler(
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
   * Executes a retryable operation with retry logic.
   *
   * <p>This method will execute the provided operation and evaluate its outcome. If the operation
   * returns a {@link NeedsRetry} outcome with a recoverable failure, it will return a {@link
   * RetryResult.Retry} indicating the next delay. If the operation fails irrecoverably or exceeds
   * the maximum number of retries, it will return a {@link RetryResult.Failure}.
   *
   * @param operation the operation to execute
   * @param numAttempts the current number of attempts made to execute the operation
   * @return the result of the retry operation
   */
  public RetryResult executeWithRetry(final RetryableOperation operation, final int numAttempts) {
    final var outcome = operation.execute();
    return switch (outcome) {
      case Success(final var cursor) -> RetryResult.success(cursor);
      case Failed(final var message, final var errorType, final var cursor) ->
          RetryResult.failure(
              new BatchOperationInitializationException(message, errorType, cursor));
      case NeedsRetry(final var cursor, final var cause) -> {
        if (shouldFailImmediately(cause) || numAttempts >= maxRetries) {
          yield RetryResult.failure(new BatchOperationInitializationException(cause, cursor));
        }
        final Duration nextDelay = calculateNextDelay(numAttempts);
        yield RetryResult.retry(nextDelay, numAttempts + 1, cursor);
      }
    };
  }

  private boolean shouldFailImmediately(final Throwable cause) {
    return cause instanceof final CamundaSearchException searchException
        && FAIL_IMMEDIATELY_REASONS.contains(searchException.getReason());
  }

  private Duration calculateNextDelay(final int attemptNumber) {
    final var calculatedDelay =
        initialRetryDelay.multipliedBy((int) Math.pow(backoffFactor, attemptNumber));
    return maxRetryDelay.compareTo(calculatedDelay) < 0 ? maxRetryDelay : calculatedDelay;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Represents the result of a retry operation.
   *
   * <p>This interface defines three possible outcomes: success, failure, and retry. Each outcome
   * carries relevant data such as batch operation key, search result cursor, or exception details.
   */
  public sealed interface RetryResult
      permits RetryResult.Success, RetryResult.Failure, RetryResult.Retry {
    static Success success(final String searchResultCursor) {
      return new Success(searchResultCursor);
    }

    static Failure failure(final BatchOperationInitializationException exception) {
      return new Failure(exception);
    }

    static Retry retry(final Duration delay, final int numAttempts, final String endCursor) {
      return new Retry(delay, numAttempts, endCursor);
    }

    record Success(String searchResultCursor) implements RetryResult {}

    record Failure(BatchOperationInitializationException exception) implements RetryResult {}

    record Retry(Duration delay, int numAttempts, String endCursor) implements RetryResult {}
  }

  /**
   * Represents a retryable operation that can be executed with retry logic.
   *
   * <p>This functional interface allows defining operations that return an {@link
   * InitializationOutcome} indicating success, need for retry, or terminal failure.
   */
  @FunctionalInterface
  public interface RetryableOperation {
    InitializationOutcome execute();
  }
}
