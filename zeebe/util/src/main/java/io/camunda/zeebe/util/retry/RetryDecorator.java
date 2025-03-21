/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.retry;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.IntervalFunction;
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

  public RetryDecorator withRetryOnException(final Predicate<Throwable> retryOnExceptionPredicate) {
    return new RetryDecorator(retryConfiguration, retryOnExceptionPredicate);
  }

  public <T> T decorate(
      final String message, final Callable<T> callable, final Predicate<T> retryPredicate)
      throws Exception {
    final Retry retry = buildRetry(message, retryPredicate);
    return Retry.decorateCallable(retry, callable).call();
  }

  public void decorate(final String message, final Runnable runnable) {
    final Retry retry = buildRetry(message, null);
    Retry.decorateRunnable(retry, runnable).run();
  }

  private <T> Retry buildRetry(final String message, final Predicate<T> retryPredicate) {
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
        .onError(event -> LOG.error("Retry failed for `{}`: {}", message, event.getLastThrowable()))
        .onRetry(
            event ->
                LOG.warn(
                    "Retrying operation for `{}`: attempt {}",
                    message,
                    event.getNumberOfRetryAttempts()));
    return retry;
  }
}
