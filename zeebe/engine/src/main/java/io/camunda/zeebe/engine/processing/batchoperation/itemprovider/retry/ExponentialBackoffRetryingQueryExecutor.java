/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider.retry;

import java.time.Duration;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link RetryingQueryExecutor} that retries operations with an exponential
 * backoff strategy.
 */
public class ExponentialBackoffRetryingQueryExecutor implements RetryingQueryExecutor {
  private static final Logger LOG =
      LoggerFactory.getLogger(ExponentialBackoffRetryingQueryExecutor.class);

  private final int maxRetries;
  private final Duration initialRetryDelay;
  private final double backoffFactor;

  /**
   * Constructor for ExponentialBackoffRetryingQueryExecutor.
   *
   * @param maxRetries the maximum number of retries to attempt after the first attempt failed
   * @param initialRetryDelay the initial delay before the first retry
   * @param backoffFactor the factor by which the retry delay increases after each attempt
   */
  public ExponentialBackoffRetryingQueryExecutor(
      final int maxRetries, final Duration initialRetryDelay, final double backoffFactor) {
    this.maxRetries = maxRetries;
    this.initialRetryDelay = initialRetryDelay;
    this.backoffFactor = backoffFactor;
  }

  @Override
  public <V> V runRetryable(final Callable<V> retryableOperation) {
    int numAttempts = maxRetries + 1; // + 1 for the initial attempt
    Duration retryDelay = initialRetryDelay;

    try {
      while (numAttempts > 0) {
        try {
          return retryableOperation.call();
        } catch (final Exception e) {
          if (shouldFailImmediately(e) || --numAttempts == 0) {
            throw new RuntimeException("Max retries reached", e);
          }
          LOG.warn(
              "Retryable operation failed, retries left: {}, retrying in {} ms. Error: {}",
              numAttempts,
              retryDelay.toMillis(),
              e.getLocalizedMessage());

          Thread.sleep(retryDelay.toMillis());
          retryDelay = retryDelay.multipliedBy((long) backoffFactor);
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt(); // Restore interrupted status
      throw new RuntimeException("Retryable operation was interrupted", e);
    }

    throw new RuntimeException("Unexpected state: should not reach here");
  }
}
