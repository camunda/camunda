/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler;

import io.camunda.migration.api.MigrationException;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MigrationHandler<T> {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final Integer backpressureDelay;

  protected MigrationHandler(final Integer backpressureDelay) {
    this.backpressureDelay = backpressureDelay;
  }

  protected boolean isConflictError(final Throwable e) {
    return (e instanceof final CompletionException completionException
            && isConflictError(completionException.getCause()))
        || (e instanceof final ServiceException serviceException
            && serviceException.getStatus() == Status.ALREADY_EXISTS);
  }

  protected boolean isNotImplementedError(final Throwable e) {
    return e instanceof final NotImplementedException exception;
  }

  protected static boolean isBackpressureError(final Throwable e) {
    return (e instanceof final CompletionException ce && isBackpressureError(ce.getCause()))
        || (e instanceof final ServiceException se && se.getStatus() == Status.RESOURCE_EXHAUSTED);
  }

  public void migrate() {
    List<T> batch;
    int page = 0;
    do {
      batch = fetchBatch(page);
      process(batch);
      page++;
    } while (!batch.isEmpty());
  }

  public String getName() {
    return getClass().getSimpleName();
  }

  protected abstract List<T> fetchBatch(int page);

  protected abstract void process(List<T> batch);

  protected void logSummary() {
    logger.info("Completed {}", getName());
  }

  protected <T> void retryOnBackpressure(
      final Supplier<T> operation, final String contextDescription) {

    while (true) {
      try {
        operation.get();
        return;
      } catch (final Exception e) {
        if (!isBackpressureError(e)) {
          throw e;
        }
        logger.warn(
            "Backpressure during {}. Retrying in {} seconds...",
            contextDescription,
            backpressureDelay);
        try {
          Thread.sleep(Duration.ofMillis(backpressureDelay));
        } catch (final InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new MigrationException("Retry interrupted during backpressure handling.", ie);
        }
      }
    }
  }
}
