/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.retry;

import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.RetryDelayStrategy;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries an operation with delays between attempts. The first attempt runs immediately; retries
 * are scheduled as actor timers, so the actor stays responsive and processes other jobs while
 * waiting for the next attempt.
 *
 * <p>Unlike {@link BackOffRetryStrategy}, exceptions thrown by the operation are not retried but
 * complete the result future exceptionally, mirroring {@link AbortableRetryStrategy}.
 */
public final class AbortableDelayedRetryStrategy implements RetryStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(AbortableDelayedRetryStrategy.class);

  private final ActorControl actor;
  private final RetryDelayStrategy delayStrategy;
  private final String operationName;

  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier currentTerminateCondition;
  private OperationToRetry currentCallable;
  private int retryCount;

  public AbortableDelayedRetryStrategy(
      final ActorControl actor, final RetryDelayStrategy delayStrategy) {
    this(actor, delayStrategy, DEFAULT_OPERATION_NAME);
  }

  public AbortableDelayedRetryStrategy(
      final ActorControl actor, final RetryDelayStrategy delayStrategy, final String operationName) {
    this.actor = actor;
    this.delayStrategy = delayStrategy;
    this.operationName = operationName;
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(final OperationToRetry callable) {
    return runWithRetry(callable, () -> false);
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(
      final OperationToRetry callable, final BooleanSupplier terminateCondition) {
    currentFuture = new CompletableActorFuture<>();
    currentTerminateCondition = terminateCondition;
    currentCallable = callable;
    retryCount = 0;
    delayStrategy.reset();

    actor.run(this::run);

    return currentFuture;
  }

  private void run() {
    try {
      if (currentCallable.run()) {
        currentFuture.complete(true);
      } else if (currentTerminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else {
        LOG.trace(
            "Operation '{}' did not complete, scheduling retry {}", operationName, ++retryCount);
        backOff();
      }
    } catch (final Exception exception) {
      currentFuture.completeExceptionally(exception);
    }
  }

  private void backOff() {
    actor.schedule(delayStrategy.nextDelay(), this::run);
  }
}
