/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.retry;

import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.function.BooleanSupplier;

public final class BackOffRetryStrategy implements RetryStrategy {

  private final ActorControl actor;
  private final Duration maxBackOff;

  private Duration backOffDuration;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier currentTerminateCondition;
  private OperationToRetry currentCallable;

  public BackOffRetryStrategy(final ActorControl actor, final Duration maxBackOff) {
    this.actor = actor;
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
    backOffDuration = Duration.ofSeconds(1);

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
      if (currentTerminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else {
        backOff();
      }
    }
  }

  private void backOff() {
    final boolean notReachedMaxBackOff = !backOffDuration.equals(maxBackOff);
    if (notReachedMaxBackOff) {
      final Duration nextBackOff = backOffDuration.multipliedBy(2);
      backOffDuration = nextBackOff.compareTo(maxBackOff) < 0 ? nextBackOff : maxBackOff;
    }
    actor.runDelayed(backOffDuration, this::run);
  }
}
