/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.engine.fetcher.EngineEntityFetcher;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.ws.rs.ClientErrorException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class RetryBackoffEngineEntityFetcher<ENG extends EngineDto> extends EngineEntityFetcher {

  @Autowired
  private BackoffCalculator backoffCalculator;

  protected RetryBackoffEngineEntityFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public void setBackoffCalculator(final BackoffCalculator backoffCalculator) {
    this.backoffCalculator = backoffCalculator;
  }

  protected <DTOS> DTOS fetchWithRetry(Supplier<DTOS> fetchFunction) {
    DTOS result = null;
    while (result == null) {
      try {
        result = fetchFunction.get();
      } catch (Exception exception) {
        logError(exception);
        long timeToSleep = backoffCalculator.calculateSleepTime();
        logDebugSleepInformation(timeToSleep);
        sleep(timeToSleep);
      }
    }
    backoffCalculator.resetBackoff();
    return result;
  }

  protected List<ENG> fetchWithRetryIgnoreClientError(Supplier<List<ENG>> fetchFunction) {
    List<ENG> result = null;
    while (result == null) {
      try {
        result = fetchFunction.get();
      } catch (Exception exception) {
        if (exception instanceof ClientErrorException) {
          logger.warn("ClientError on fetching entity: {}", exception.getMessage(), exception);
          result = new ArrayList<>();
        } else {
          logError(exception);
          long timeToSleep = backoffCalculator.calculateSleepTime();
          logDebugSleepInformation(timeToSleep);
          sleep(timeToSleep);
        }
      }
    }
    backoffCalculator.resetBackoff();
    return result;
  }

  private void sleep(long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.debug("Was interrupted from sleep. Continuing to fetch new entities.", e);
    }
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
      "Sleeping for [{}] ms and retrying the fetching of the entities afterwards.",
      sleepTime
    );
  }

  private void logError(Exception e) {
    logger.error(
      "Error during fetching of entities. Please check the connection with [{}]!",
      engineContext.getEngineAlias(),
      e
    );
  }

}
