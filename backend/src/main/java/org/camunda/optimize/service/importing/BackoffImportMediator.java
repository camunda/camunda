/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.ImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public abstract class BackoffImportMediator<T extends ImportIndexHandler<?, ?>> implements EngineImportMediator {
  protected Logger logger = LoggerFactory.getLogger(getClass());
  protected ConfigurationService configurationService;
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected BackoffCalculator idleBackoffCalculator;

  private final BackoffCalculator errorBackoffCalculator = new BackoffCalculator(10, 1000);

  protected T importIndexHandler;

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    boolean pageIsPresent = importNextPageRetryOnError(importCompleted);
    if (pageIsPresent) {
      idleBackoffCalculator.resetBackoff();
    } else {
      calculateNewDateUntilIsBlocked();
    }
    return importCompleted;
  }

  /**
   * Method is invoked by scheduler once no more jobs are created by factories
   * associated with import process from specific engine
   *
   * @return time to sleep for import process of an engine in general
   */
  public long getBackoffTimeInMs() {
    return idleBackoffCalculator.getTimeUntilNextRetry();
  }

  @Override
  public void resetBackoff() {
    idleBackoffCalculator.resetBackoff();
  }

  @Override
  public boolean canImport() {
    boolean canImportNewPage = idleBackoffCalculator.isReadyForNextRetry();
    logger.debug("can import next page [{}]", canImportNewPage);
    return canImportNewPage;
  }

  @Override
  public ImportJobExecutor getImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  public T getImportIndexHandler() {
    return importIndexHandler;
  }

  @Override
  public void shutdown() {
    elasticsearchImportJobExecutor.stopExecutingImportJobs();
  }

  protected abstract boolean importNextPage(Runnable importCompleteCallback);

  private boolean importNextPageRetryOnError(final CompletableFuture<Void> importCompleteCallback) {
    Boolean result = null;
    while (result == null) {
      try {
        result = importNextPage(() -> importCompleteCallback.complete(null));
      } catch (final Exception e) {
        if (errorBackoffCalculator.isMaximumBackoffReached()) {
          // if max back-off is reached abort retrying and return true to indicate there is new data
          logger.error("Was not able to import next page and reached max backoff, aborting this run.", e);
          importCompleteCallback.complete(null);
          result = true;
        } else {
          long timeToSleep = errorBackoffCalculator.calculateSleepTime();
          logger.error("Was not able to import next page, retrying after sleeping for {}ms.", timeToSleep, e);
          sleep(timeToSleep);
        }
      }
    }
    errorBackoffCalculator.resetBackoff();

    return result;
  }

  private void calculateNewDateUntilIsBlocked() {
    if (idleBackoffCalculator.isMaximumBackoffReached()) {
      logger.debug(
        "Maximum idle backoff reached, this mediator will not backoff any further than {}s.",
        idleBackoffCalculator.getMaximumBackoffSeconds()
      );
    }
    final long sleepTime = idleBackoffCalculator.calculateSleepTime();
    logger.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }

  private void sleep(final long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.debug("Was interrupted from sleep. Continuing.", e);
    }
  }

}
