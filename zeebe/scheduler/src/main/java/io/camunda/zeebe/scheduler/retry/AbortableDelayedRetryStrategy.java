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

/**
 * Retries an operation with delays between attempts. The first attempt runs immediately; retries
 * are scheduled as actor timers, so the actor stays responsive and processes other jobs while
 * waiting for the next attempt.
 *
 * <p>Unlike {@link BackOffRetryStrategy}, exceptions thrown by the operation are not retried but
 * complete the result future exceptionally, mirroring {@link AbortableRetryStrategy}.
 */
public final class AbortableDelayedRetryStrategy implements RetryStrategy {

  private final ActorControl actor;
  private final RetryDelayStrategy delayStrategy;

  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier currentTerminateCondition;
  private OperationToRetry currentCallable;

  public AbortableDelayedRetryStrategy(
      final ActorControl actor, final RetryDelayStrategy delayStrategy) {
    this.actor = actor;
    this.delayStrategy = delayStrategy;
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
