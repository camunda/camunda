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
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;

public abstract class BackoffImportMediator<T extends ImportIndexHandler<?, ?>> implements EngineImportMediator {
  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected BeanFactory beanFactory;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  @Autowired
  private BackoffCalculator idleBackoffCalculator;

  private final BackoffCalculator errorBackoffCalculator = new BackoffCalculator(10, 1000);

  protected T importIndexHandler;

  @PostConstruct
  private void initialize() {
    init();
  }

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

  @Override
  public void shutdown() {
    elasticsearchImportJobExecutor.stopExecutingImportJobs();
  }

  protected abstract void init();

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

  private void executeAfterMaxBackoffIsReached() {
    importIndexHandler.resetImportIndex();
  }

  private void calculateNewDateUntilIsBlocked() {
    if (idleBackoffCalculator.isMaximumBackoffReached()) {
      executeAfterMaxBackoffIsReached();
    }
    logDebugSleepInformation(idleBackoffCalculator.calculateSleepTime());
  }

  private void sleep(final long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.debug("Was interrupted from sleep. Continuing.", e);
    }
  }

  private void logDebugSleepInformation(final long sleepTime) {
    logger.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }

}
