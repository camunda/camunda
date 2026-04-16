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
import io.camunda.zeebe.util.exception.RecoverableException;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecoverableRetryStrategy implements RetryStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(RecoverableRetryStrategy.class);
  private static final ThrottledLogger THROTTLED_LOG =
      new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private final ActorControl actor;
  private final ActorRetryMechanism retryMechanism;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier terminateCondition;

  public RecoverableRetryStrategy(final ActorControl actor) {
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
    } catch (final RecoverableException ex) {
      if (!terminateCondition.getAsBoolean()) {
        THROTTLED_LOG.warn("Caught recoverable exception, will retry: {}", ex.getMessage(), ex);
        actor.run(this::run);
        actor.yieldThread();
      }
    } catch (final Exception exception) {
      currentFuture.completeExceptionally(exception);
    }
  }
}
