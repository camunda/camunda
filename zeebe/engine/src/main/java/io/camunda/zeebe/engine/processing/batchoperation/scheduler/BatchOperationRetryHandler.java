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
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializer.BatchOperationInitializationException;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializer.BatchOperationInitializationResult;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
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
   * <p>This method will attempt to execute the provided operation, handling retries according to
   * the configured parameters. If the operation fails with a recoverable exception, it will return
   * a {@link RetryResult.Retry} indicating the next delay and updated context. If the operation
   * fails irrecoverably or exceeds the maximum number of retries, it will return a {@link
   * RetryResult.Failure}.
   *
   * @param operation the operation to execute
   * @param numAttempts the current number of attempts made to execute the operation
   * @return the result of the retry operation
   */
  public RetryResult executeWithRetry(final RetryableOperation operation, final int numAttempts) {
    try {
      final var result = operation.execute();
      return RetryResult.success(result.searchResultCursor());
    } catch (final BatchOperationInitializationException e) {
      if (shouldFailImmediately(e) || numAttempts >= maxRetries) {
        return RetryResult.failure(e);
      }
      final Duration nextDelay = calculateNextDelay(numAttempts);
      return RetryResult.retry(nextDelay, numAttempts + 1, e.getEndCursor());
    }
  }

  private boolean shouldFailImmediately(final BatchOperationInitializationException exception) {
    if (BatchOperationErrorType.RESULT_BUFFER_SIZE_EXCEEDED.equals(exception.getErrorType())) {
      return true;
    }
    return exception.getCause() instanceof CamundaSearchException
        && FAIL_IMMEDIATELY_REASONS.contains(
            ((CamundaSearchException) exception.getCause()).getReason());
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
   * <p>This functional interface allows defining operations that may require retries due to
   * transient failures or other conditions.
   */
  @FunctionalInterface
  public interface RetryableOperation {
    BatchOperationInitializationResult execute() throws BatchOperationInitializationException;
  }
}
