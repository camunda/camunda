/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.retry;

import static java.util.Optional.ofNullable;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.CheckedRunnable;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryDecorator {

  private static final Logger LOG = LoggerFactory.getLogger(RetryDecorator.class);
  private final RetryConfiguration retryConfiguration;
  private final Predicate<Throwable> retryOnExceptionPredicate;

  public RetryDecorator(
      final RetryConfiguration retryConfiguration,
      final Predicate<Throwable> retryOnExceptionPredicate) {
    this.retryConfiguration = retryConfiguration;
    this.retryOnExceptionPredicate = retryOnExceptionPredicate;
  }

  public RetryDecorator(final RetryConfiguration retryConfiguration) {
    this(retryConfiguration, e -> true);
  }

  public RetryDecorator() {
    this(new RetryConfiguration());
  }

  public RetryDecorator withRetryOnException(final Predicate<Throwable> retryOnExceptionPredicate) {
    return new RetryDecorator(retryConfiguration, retryOnExceptionPredicate);
  }

  public RetryDecorator withRetryOnExceptions(
      final Class<? extends Throwable>... exceptionClasses) {
    return new RetryDecorator(
        retryConfiguration,
        e -> {
          for (final var exceptionClass : exceptionClasses) {
            if (exceptionClass.isAssignableFrom(e.getClass())) {
              return true;
            }
          }
          return false;
        });
  }

  public <T> T decorate(
      final String operationName, final Callable<T> callable, final Predicate<T> retryPredicate)
      throws Exception {
    final Retry retry = buildRetry(operationName, retryPredicate);
    return Retry.decorateCallable(retry, callable).call();
  }

  public <T> T decorate(final String operationName, final Callable<T> callable) throws Exception {
    final Retry retry = buildRetry(operationName, null);
    return Retry.decorateCallable(retry, callable).call();
  }

  /**
   * Caution: This method will retry only on unchecked exceptions (extending RuntimeException).
   * Sneaky throws of checked exceptions will not be retried.
   */
  public void decorate(final String operationName, final Runnable runnable) {
    final Retry retry = buildRetry(operationName, null);
    Retry.decorateRunnable(retry, runnable).run();
  }

  public void decorateCheckedRunnable(final String operationName, final CheckedRunnable runnable) {
    final Retry retry = buildRetry(operationName, null);
    Retry.decorateCheckedRunnable(retry, runnable).unchecked().run();
  }

  private <T> Retry buildRetry(final String operationName, final Predicate<T> retryPredicate) {
    final var retryConfigBuilder =
        RetryConfig.<T>custom()
            .maxAttempts(retryConfiguration.getMaxRetries())
            .intervalBiFunction(
                IntervalBiFunction.ofIntervalFunction(
                    IntervalFunction.ofExponentialRandomBackoff(
                        retryConfiguration.getMinRetryDelay(),
                        retryConfiguration.getRetryDelayMultiplier(),
                        retryConfiguration.getMaxRetryDelay())))
            .retryOnException(retryOnExceptionPredicate);
    if (retryPredicate != null) {
      retryConfigBuilder.retryOnResult(retryPredicate);
    }
    final var config = retryConfigBuilder.build();
    final Retry retry = Retry.of(UUID.randomUUID().toString(), config);

    retry
        .getEventPublisher()
        .onError(
            event ->
                ofNullable(event.getLastThrowable())
                    .ifPresentOrElse(
                        t -> LOG.error("Retry failed for '{}': {}", operationName, t.getMessage()),
                        () -> LOG.error("Retry failed for '{}'", operationName)))
        .onRetry(
            event -> {
              ofNullable(event.getLastThrowable())
                  .ifPresentOrElse(
                      t -> {
                        LOG.warn(
                            "Retrying operation for '{}': attempt {}. Message: {}.",
                            operationName,
                            event.getNumberOfRetryAttempts(),
                            t.getMessage());
                        LOG.debug("Stacktrace:", t);
                      },
                      () ->
                          LOG.warn(
                              "Retrying operation for '{}': attempt {}.",
                              operationName,
                              event.getNumberOfRetryAttempts()));
            });
    return retry;
  }
}
