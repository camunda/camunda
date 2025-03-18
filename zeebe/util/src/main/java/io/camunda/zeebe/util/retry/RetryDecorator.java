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
import io.github.resilience4j.retry.RetryRegistry;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryDecorator {

  private static final Logger LOG = LoggerFactory.getLogger(RetryDecorator.class);
  private static final double RETRY_DELAY_MULTIPLIER = 2;
  private final RetryConfiguration retryConfiguration;
  private final Predicate<Throwable> retryOnExceptionPredicate;

  public RetryDecorator(
      final RetryConfiguration retryConfiguration,
      final Predicate<Throwable> retryOnExceptionPredicate) {
    this.retryConfiguration = retryConfiguration;
    this.retryOnExceptionPredicate = retryOnExceptionPredicate;
  }

  public RetryDecorator(final RetryConfiguration retryConfiguration) {
    this(retryConfiguration, e -> false);
  }

  public RetryDecorator withRetryOnException(final Predicate<Throwable> retryOnExceptionPredicate) {
    return new RetryDecorator(retryConfiguration, retryOnExceptionPredicate);
  }

  public RetryDecorator withRetryOnAllExceptions() {
    return new RetryDecorator(retryConfiguration, e -> true);
  }

  public <T> T decorate(
      final String message, final Callable<T> callable, final Predicate<T> retryPredicate)
      throws Exception {
    final Retry retry = buildRetry(message, retryPredicate);
    return Retry.decorateCallable(retry, callable).call();
  }

  public void decorate(final String message, final Runnable callable) {
    final Retry retry = buildRetry(message, null);
    Retry.decorateRunnable(retry, callable).run();
  }

  private <T> Retry buildRetry(final String message, final Predicate<T> retryPredicate) {
    final var retryConfigBuilder =
        RetryConfig.<T>custom()
            .maxAttempts(retryConfiguration.getMaxRetries())
            .intervalBiFunction(
                IntervalBiFunction.ofIntervalFunction(
                    IntervalFunction.ofExponentialRandomBackoff(
                        retryConfiguration.getMinRetryDelay(),
                        RETRY_DELAY_MULTIPLIER,
                        retryConfiguration.getMaxRetryDelay())))
            .retryOnException(retryOnExceptionPredicate);
    if (retryPredicate != null) {
      retryConfigBuilder.retryOnResult(retryPredicate);
    }
    final var config = retryConfigBuilder.build();
    final RetryRegistry registry = RetryRegistry.of(config);
    final Retry retry = registry.retry("operation-retry");

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
