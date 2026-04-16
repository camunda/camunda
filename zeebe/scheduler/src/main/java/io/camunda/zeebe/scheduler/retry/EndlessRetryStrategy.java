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
  private final int maxRetries;
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier terminateCondition;
  private int retryCount;

  public EndlessRetryStrategy(final ActorControl actor) {
    this(actor, Integer.MAX_VALUE);
  }

  public EndlessRetryStrategy(final ActorControl actor, final int maxRetries) {
    this.actor = actor;
    this.maxRetries = maxRetries;
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
    retryCount = 0;
    retryMechanism.wrap(callable, terminateCondition, currentFuture);

    actor.run(this::run);

    return currentFuture;
  }

  private void run() {
    try {
      final var control = retryMechanism.run();
      if (control == Control.RETRY) {
        retryCount++;
        if (retryCount > maxRetries) {
          LOG.error("Retry limit reached ({} retries). Failing operation.", maxRetries);
          currentFuture.completeExceptionally(new RetryLimitExceededException(maxRetries, null));
        } else {
          actor.run(this::run);
          actor.yieldThread();
        }
      }
    } catch (final Exception exception) {
      if (terminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else {
        retryCount++;
        if (retryCount > maxRetries) {
          LOG.error("Retry limit reached ({} retries). Failing operation.", maxRetries, exception);
          currentFuture.completeExceptionally(
              new RetryLimitExceededException(maxRetries, exception));
        } else {
          actor.run(this::run);
          actor.yieldThread();
          LOG.error(
              "Caught exception {} with message {} (retry {}/{}), will retry...",
              exception.getClass(),
              exception.getMessage(),
              retryCount,
              maxRetries,
              exception);
        }
      }
    }
  }
}
