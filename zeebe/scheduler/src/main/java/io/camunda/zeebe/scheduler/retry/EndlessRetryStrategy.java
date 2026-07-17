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
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EndlessRetryStrategy implements RetryStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(EndlessRetryStrategy.class);

  private final ActorControl actor;
  private final ActorRetryMechanism retryMechanism;
  private final int maxRetries;
  private final ThrottledLogger throttledLog = new ThrottledLogger(LOG, Duration.ofSeconds(5));
  private final AtomicInteger retryCount = new AtomicInteger();
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier terminateCondition;

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
    retryCount.set(0);
    retryMechanism.wrap(callable, terminateCondition, currentFuture);

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
        if (!retryLimitExceeded(
            retryCount.incrementAndGet(), maxRetries, null, LOG, currentFuture)) {
          actor.run(this::run);
          actor.yieldThread();
        }
      }
    } catch (final Exception exception) {
      if (terminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else {
        final int retries = retryCount.incrementAndGet();
        if (!retryLimitExceeded(retries, maxRetries, exception, LOG, currentFuture)) {
          throttledLog.warn(
              "Caught recoverable exception (retry {}/{}), will retry: {}",
              retries,
              maxRetries,
              exception.getMessage(),
              exception);
          actor.run(this::run);
          actor.yieldThread();
        }
      }
    }
  }
}
