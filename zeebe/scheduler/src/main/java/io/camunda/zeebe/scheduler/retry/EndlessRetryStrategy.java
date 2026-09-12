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
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EndlessRetryStrategy implements RetryStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(EndlessRetryStrategy.class);

  private final ActorControl actor;
  private final ActorRetryMechanism retryMechanism;
  private final int maxRetries;
  private final @Nullable String operationName;
  private final ThrottledLogger throttledLog = new ThrottledLogger(LOG, Duration.ofSeconds(5));
  private CompletableActorFuture<Boolean> currentFuture;
  private BooleanSupplier terminateCondition;
  private int retryCount;

  public EndlessRetryStrategy(final ActorControl actor) {
    this(actor, Integer.MAX_VALUE);
  }

  public EndlessRetryStrategy(final ActorControl actor, final int maxRetries) {
    this(actor, maxRetries, null);
  }

  public EndlessRetryStrategy(
      final ActorControl actor, final int maxRetries, final @Nullable String operationName) {
    this.actor = actor;
    this.maxRetries = maxRetries;
    this.operationName = operationName;
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
        if (!retryLimitExceeded(++retryCount, maxRetries, null, LOG, currentFuture)) {
          if (operationName != null) {
            LOG.trace(
                "Operation '{}' did not complete, scheduling retry {}/{}",
                operationName,
                retryCount,
                maxRetries);
          }
          actor.run(this::run);
          actor.yieldThread();
        }
      }
    } catch (final Exception exception) {
      if (terminateCondition.getAsBoolean()) {
        currentFuture.complete(false);
      } else if (!retryLimitExceeded(++retryCount, maxRetries, exception, LOG, currentFuture)) {
        throttledLog.warn(
            "Operation '{}' caught recoverable exception (retry {}/{}), will retry: {}",
            operationName != null ? operationName : DEFAULT_OPERATION_NAME,
            retryCount,
            maxRetries,
            exception.getMessage(),
            exception);
        actor.run(this::run);
        actor.yieldThread();
      }
    }
  }
}
