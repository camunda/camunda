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
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EndlessRetryStrategy implements RetryStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(EndlessRetryStrategy.class);

  private final ActorControl actor;
  private final ActorRetryMechanism retryMechanism;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier terminateCondition;

  public EndlessRetryStrategy(final ActorControl actor) {
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
    terminateCondition = condition;
    retryMechanism.wrap(callable, terminateCondition, currentFuture);

    actor.run(this::run);

    return currentFuture;
  }

  private void run() {
    try {
      final var control = retryMechanism.run();
      if (control == Control.RETRY) {
        actor.run(this::run);
        actor.yieldThread();
      }
    } catch (final Exception exception) {
      if (terminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else {
        actor.run(this::run);
        actor.yieldThread();
        LOG.error(
            "Caught exception {} with message {}, will retry...",
            exception.getClass(),
            exception.getMessage(),
            exception);
      }
    }
  }
}
