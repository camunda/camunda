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
import io.camunda.zeebe.scheduler.retry.ActorRetryMechanism.Control;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class AbortableRetryStrategy implements RetryStrategy {

  private final ActorControl actor;
  private final ActorRetryMechanism retryMechanism;
  private final AtomicInteger retryCount = new AtomicInteger();
  private CompletableActorFuture<Boolean> currentFuture;

  public AbortableRetryStrategy(final ActorControl actor) {
    this.actor = actor;
    retryMechanism = new ActorRetryMechanism();
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(final OperationToRetry callable) {
    return runWithRetry(callable, () -> false);
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(
      final OperationToRetry callable, final BooleanSupplier condition) {
    currentFuture = new CompletableActorFuture<>();
    retryCount.set(0);
    retryMechanism.wrap(callable, condition, currentFuture);

    actor.run(this::run);

    return currentFuture;
  }

  @Override
  public int getRetryCount() {
    return retryCount.get();
  }

  private void run() {
    try {
      final var control = retryMechanism.run();
      if (control == Control.RETRY) {
        retryCount.incrementAndGet();
        actor.run(this::run);
        actor.yieldThread();
      }
    } catch (final Exception exception) {
      currentFuture.completeExceptionally(exception);
    }
  }
}
