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
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Retries an operation with exponential backoff between attempts. The first attempt runs
 * immediately; retries are scheduled as actor timers, so the actor stays responsive and processes
 * other jobs while waiting for the next attempt.
 *
 * <p>Unlike {@link BackOffRetryStrategy}, exceptions thrown by the operation are not retried but
 * complete the result future exceptionally, mirroring {@link AbortableRetryStrategy}.
 */
public final class AbortableBackOffRetryStrategy implements RetryStrategy {

  private final ActorControl actor;
  private final Duration maxBackOff;
  private final Duration initialBackOff;

  private Duration backOffDuration;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier currentTerminateCondition;
  private OperationToRetry currentCallable;

  public AbortableBackOffRetryStrategy(
      final ActorControl actor, final Duration initialBackOff, final Duration maxBackOff) {
    this.actor = actor;
    this.initialBackOff = initialBackOff;
    this.maxBackOff = maxBackOff;
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
    backOffDuration = initialBackOff;

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
    actor.schedule(backOffDuration, this::run);
    final var nextBackOff = backOffDuration.multipliedBy(2);
    backOffDuration = nextBackOff.compareTo(maxBackOff) < 0 ? nextBackOff : maxBackOff;
  }
}
