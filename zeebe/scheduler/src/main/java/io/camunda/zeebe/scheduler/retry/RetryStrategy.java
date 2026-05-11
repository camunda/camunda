/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.retry;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

public interface RetryStrategy {

  /**
   * Runs the given runnable with the defined retry strategy.
   *
   * <p>Returns an actor future, which will be completed when the callable was successfully executed
   * and has returned true.
   *
   * @param callable the callable which should be executed
   * @return a future, which is completed with true if the execution was successful
   */
  ActorFuture<Boolean> runWithRetry(OperationToRetry callable);

  /**
   * Runs the given runnable with the defined retry strategy.
   *
   * <p>Returns an actor future, which will be completed when the callable was successfully executed
   * and has returned true.
   *
   * @param callable the callable which should be executed
   * @param terminateCondition condition is called when callable returns false, if terminate
   *     condition returns true the retry strategy is aborted
   * @return a future, which is completed with true if the execution was successful
   */
  ActorFuture<Boolean> runWithRetry(OperationToRetry callable, BooleanSupplier terminateCondition);

  /**
   * Checks whether the retry limit has been exceeded. If the limit is exceeded, logs an error and
   * completes the given future exceptionally with a {@link RetryLimitExceededException}.
   *
   * @param retryCount the current retry count (already incremented by the caller)
   * @param maxRetries the configured maximum number of retries
   * @param cause the exception that triggered the retry, or null if the retry was triggered by a
   *     false return
   * @param log the logger to use for error logging
   * @param currentFuture the future to complete exceptionally if the limit is exceeded
   * @return true if the retry limit has been exceeded, false otherwise
   */
  default boolean retryLimitExceeded(
      final int retryCount,
      final int maxRetries,
      final Throwable cause,
      final Logger log,
      final CompletableActorFuture<Boolean> currentFuture) {
    if (retryCount > maxRetries) {
      log.error("Retry limit reached ({} retries). Failing operation.", maxRetries, cause);
      currentFuture.completeExceptionally(new RetryLimitExceededException(maxRetries, cause));
      return true;
    }
    return false;
  }
}
