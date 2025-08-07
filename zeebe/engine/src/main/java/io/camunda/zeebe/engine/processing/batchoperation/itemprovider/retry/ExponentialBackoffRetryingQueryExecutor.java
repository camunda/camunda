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

  public static final String MSG_MAX_RETRIES_REACHED =
      "Tried to run query %d times, but it failed every time. Giving up.";
  public static final String MSG_INTERRUPTED = "Interrupted while waiting for next query attempt";

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

    while (numAttempts > 0) {
      try {
        return retryableOperation.call();
      } catch (final Exception e) {
        if (shouldFailImmediately(e) || --numAttempts == 0) {
          throw new RetryAttemptsExceededException(
              String.format(MSG_MAX_RETRIES_REACHED, maxRetries + 1), e);
        }
        // the backoff is potentially a double value so we need to convert to millis in between
        retryDelay = Duration.ofMillis(Math.round(retryDelay.toMillis() * backoffFactor));
        LOG.warn(
            "Retryable operation failed, retries left: {}, retrying in {} ms. Error: {}",
            numAttempts,
            retryDelay.toMillis(),
            e.getLocalizedMessage());

        sleep(retryDelay);
      }
    }

    throw new IllegalStateException(
        "Unexpected state: Used all query retry attempts without returning a value");
  }

  private static void sleep(final Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new ExecutorInterruptedException(MSG_INTERRUPTED, ie);
    }
  }
}
