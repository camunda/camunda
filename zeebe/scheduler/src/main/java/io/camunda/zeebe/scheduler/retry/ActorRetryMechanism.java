/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.retry;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.BooleanSupplier;

public final class ActorRetryMechanism {

  /** A value indicating that no maximum retry limit is set (unlimited retries). */
  static final int UNLIMITED = -1;

  private OperationToRetry currentCallable;
  private BooleanSupplier currentTerminateCondition;
  private ActorFuture<Boolean> currentFuture;
  private int maxRetries = UNLIMITED;
  private int retryCount;

  void wrap(
      final OperationToRetry callable,
      final BooleanSupplier condition,
      final ActorFuture<Boolean> resultFuture) {
    currentCallable = callable;
    currentTerminateCondition = condition;
    currentFuture = resultFuture;
    retryCount = 0;
  }

  void setMaxRetries(final int maxRetries) {
    this.maxRetries = maxRetries;
  }

  int getRetryCount() {
    return retryCount;
  }

  Control run() throws Exception {
    if (currentCallable.run()) {
      currentFuture.complete(true);
      return Control.DONE;
    } else if (currentTerminateCondition.getAsBoolean()) {
      currentFuture.complete(false);
      return Control.DONE;
    } else {
      retryCount++;
      if (maxRetries != UNLIMITED && retryCount > maxRetries) {
        currentFuture.completeExceptionally(
            new MaxRetryReachedException(
                "Max retry count of " + maxRetries + " reached, aborting retry."));
        return Control.DONE;
      }
      return Control.RETRY;
    }
  }

  /**
   * Increments the retry count and checks if the max retry limit has been reached. This is used by
   * retry strategies that catch exceptions and retry without going through {@link #run()}.
   *
   * @return true if the max retry limit has been reached and the future has been completed
   *     exceptionally
   */
  boolean incrementAndCheckLimit() {
    retryCount++;
    if (maxRetries != UNLIMITED && retryCount > maxRetries) {
      currentFuture.completeExceptionally(
          new MaxRetryReachedException(
              "Max retry count of " + maxRetries + " reached, aborting retry."));
      return true;
    }
    return false;
  }

  enum Control {
    RETRY,
    DONE;
  }
}
