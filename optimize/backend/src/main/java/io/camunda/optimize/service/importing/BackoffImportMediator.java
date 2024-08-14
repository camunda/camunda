/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BackoffImportMediator<T extends EngineImportIndexHandler<?, ?>, DTO>
    implements ImportMediator {

  protected Logger logger = LoggerFactory.getLogger(getClass());
  protected ConfigurationService configurationService;
  protected BackoffCalculator idleBackoffCalculator;
  protected T importIndexHandler;
  protected ImportService<DTO> importService;
  private final BackoffCalculator errorBackoffCalculator = new BackoffCalculator(10, 1000);

  protected BackoffImportMediator(
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator,
      final T importIndexHandler,
      final ImportService<DTO> importService) {
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
    this.importIndexHandler = importIndexHandler;
    this.importService = importService;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    final boolean pageIsPresent = importNextPageRetryOnError(importCompleted);
    if (pageIsPresent) {
      idleBackoffCalculator.resetBackoff();
    } else {
      calculateNewDateUntilIsBlocked();
    }
    return importCompleted;
  }

  /**
   * Method is invoked by scheduler once no more jobs are created by factories associated with
   * import process from specific engine
   *
   * @return time to sleep for import process of an engine in general
   */
  @Override
  public long getBackoffTimeInMs() {
    return idleBackoffCalculator.getTimeUntilNextRetry();
  }

  @Override
  public void resetBackoff() {
    idleBackoffCalculator.resetBackoff();
  }

  @Override
  public boolean canImport() {
    final boolean canImportNewPage = idleBackoffCalculator.isReadyForNextRetry();
    logger.debug("can import next page [{}]", canImportNewPage);
    return canImportNewPage;
  }

  @Override
  public boolean hasPendingImportJobs() {
    return importService.hasPendingImportJobs();
  }

  @Override
  public void shutdown() {
    importService.shutdown();
  }

  public T getImportIndexHandler() {
    return importIndexHandler;
  }

  protected abstract boolean importNextPage(Runnable importCompleteCallback);

  private boolean importNextPageRetryOnError(final CompletableFuture<Void> importCompleteCallback) {
    Boolean result = null;
    try {
      while (result == null) {
        try {
          result = importNextPage(() -> importCompleteCallback.complete(null));
        } catch (final IllegalStateException e) {
          throw e;
        } catch (final Exception e) {
          if (errorBackoffCalculator.isMaximumBackoffReached()) {
            // if max back-off is reached abort retrying and return true to indicate there is new
            // data
            logger.error(
                "Was not able to import next page and reached max backoff, aborting this run.", e);
            importCompleteCallback.complete(null);
            result = true;
          } else {
            final long timeToSleep = errorBackoffCalculator.calculateSleepTime();
            logger.error(
                "Was not able to import next page, retrying after sleeping for {}ms.",
                timeToSleep,
                e);
            Thread.sleep(timeToSleep);
          }
        }
      }
    } catch (final InterruptedException e) {
      logger.warn("Was interrupted while importing next page.", e);
      Thread.currentThread().interrupt();
      return false;
    }
    errorBackoffCalculator.resetBackoff();

    return result;
  }

  private void calculateNewDateUntilIsBlocked() {
    if (idleBackoffCalculator.isMaximumBackoffReached()) {
      logger.debug(
          "Maximum idle backoff reached, this mediator will not backoff any further than {}ms.",
          idleBackoffCalculator.getMaximumBackoffMilliseconds());
    }
    final long sleepTime = idleBackoffCalculator.calculateSleepTime();
    logger.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }
}
