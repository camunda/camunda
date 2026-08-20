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

public final class BackOffRetryStrategy implements RetryStrategy {

  private final ActorControl actor;
  private final Duration maxBackOff;
  private final Duration initialBackOff;

  private Duration backOffDuration;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier currentTerminateCondition;
  private OperationToRetry currentCallable;

  public BackOffRetryStrategy(final ActorControl actor, final Duration maxBackOff) {
    this(actor, maxBackOff, Duration.ofSeconds(1));
  }

  public BackOffRetryStrategy(
      final ActorControl actor, final Duration maxBackOff, final Duration initialBackOff) {
    this.actor = actor;
    this.maxBackOff = maxBackOff;
    this.initialBackOff = initialBackOff;
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
    actor.schedule(backOffDuration, this::run);
  }
}
