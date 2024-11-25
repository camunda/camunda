/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.util;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.migration.process.config.ProcessMigrationProperties;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.generic.OpenSearchClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdapterRetryDecorator {

  private static final Logger LOG = LoggerFactory.getLogger(AdapterRetryDecorator.class);
  private static final double RETRY_DELAY_MULTIPLIER = 2;
  private final ProcessMigrationProperties properties;

  public AdapterRetryDecorator(final ProcessMigrationProperties properties) {
    this.properties = properties;
  }

  public <T> T decorate(
      final String message, final Callable<T> callable, final Predicate<T> retryPredicate)
      throws Exception {

    final Retry retry = buildRetry(message, retryPredicate);
    return Retry.decorateCallable(retry, callable).call();
  }

  private <T> Retry buildRetry(final String message, final Predicate<T> retryPredicate) {
    final RetryConfig config =
        RetryConfig.<T>custom()
            .maxAttempts(properties.getMaxRetries())
            .waitDuration(properties.getMinRetryDelay())
            .retryOnResult(retryPredicate)
            .intervalBiFunction(
                IntervalBiFunction.ofIntervalFunction(
                    IntervalFunction.ofExponentialRandomBackoff(
                        properties.getMinRetryDelay(),
                        RETRY_DELAY_MULTIPLIER,
                        properties.getMaxRetryDelay())))
            .retryOnException(
                throwable ->
                    throwable instanceof IOException
                        || throwable instanceof ElasticsearchException
                        || throwable instanceof OpenSearchException
                        || throwable instanceof OpenSearchClientException)
            .build();

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
