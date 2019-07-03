/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.ImportJobExecutor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;

@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class BackoffImportMediator<T extends ImportIndexHandler> implements EngineImportMediator {
  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected BeanFactory beanFactory;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  @Autowired
  protected ImportIndexHandlerProvider provider;
  @Autowired
  private BackoffCalculator idleBackoffCalculator;
  @Autowired
  private BackoffCalculator errorBackoffCalculator;

  protected final EngineContext engineContext;
  protected T importIndexHandler;

  public BackoffImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  private void initialize() {
    init();
  }

  @Override
  public void importNextPage() {
    if (idleBackoffCalculator.isReadyForNextRetry()) {
      boolean pageIsPresent = importNextPageRetryOnError();
      if (pageIsPresent) {
        idleBackoffCalculator.resetBackoff();
      } else {
        calculateNewDateUntilIsBlocked();
      }
    }
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

  protected abstract void init();

  protected abstract boolean importNextEnginePage();

  private boolean importNextPageRetryOnError() {
    Boolean result = null;
    while (result == null) {
      try {
        result = importNextEnginePage();
      } catch (final Exception e) {
        if (errorBackoffCalculator.isMaximumBackoffReached()) {
          // if max back-off is reached abort retrying and return true to indicate there is new data
          logger.error("Was not able to import next page and reached max backoff, aborting this run.", e);
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
    importIndexHandler.executeAfterMaxBackoffIsReached();
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
