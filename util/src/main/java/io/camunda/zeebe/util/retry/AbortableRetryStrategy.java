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
import java.util.function.BooleanSupplier;

public final class AbortableRetryStrategy implements RetryStrategy {

  private final ActorControl actor;
  private final ActorRetryMechanism retryMechanism;
  private CompletableActorFuture<Boolean> currentFuture;

  public AbortableRetryStrategy(final ActorControl actor) {
    this.actor = actor;
    retryMechanism = new ActorRetryMechanism(actor);
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(final OperationToRetry callable) {
    return runWithRetry(callable, () -> false);
  }

  @Override
  public ActorFuture<Boolean> runWithRetry(
      final OperationToRetry callable, final BooleanSupplier condition) {
    currentFuture = new CompletableActorFuture<>();
    retryMechanism.wrap(callable, condition, currentFuture);

    actor.runUntilDone(this::run);

    return currentFuture;
  }

  private void run() {
    try {
      retryMechanism.run();
    } catch (final Exception exception) {
      currentFuture.completeExceptionally(exception);
      actor.done();
    }
  }
}
