/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.fetcher.EngineEntityFetcher;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import java.util.function.Supplier;

@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class RetryBackoffEngineEntityFetcher extends EngineEntityFetcher {

  @Autowired
  protected BackoffCalculator backoffCalculator;

  protected RetryBackoffEngineEntityFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public void setBackoffCalculator(final BackoffCalculator backoffCalculator) {
    this.backoffCalculator = backoffCalculator;
  }

  protected <DTOS> DTOS fetchWithRetry(Supplier<DTOS> fetchFunction) {
    DTOS result = null;
    try {
      while (result == null) {
        try {
          result = fetchFunction.get();
        } catch (IllegalStateException e) {
          throw e;
        } catch (Exception exception) {
          logError(exception);
          long timeToSleep = backoffCalculator.calculateSleepTime();
          logDebugSleepInformation(timeToSleep);
          Thread.sleep(timeToSleep);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OptimizeRuntimeException("Was interrupted while fetching.", e);
    }
    backoffCalculator.resetBackoff();
    return result;
  }

  protected void logDebugSleepInformation(long sleepTime) {
    logger.debug(
      "Sleeping for [{}] ms and retrying the fetching of the entities afterwards.",
      sleepTime
    );
  }

  protected void logError(Exception e) {
    StringBuilder errorMessageBuilder = new StringBuilder();
    errorMessageBuilder.append(String.format(
      "Error during fetching of entities. Please check the connection with [%s]!",
      engineContext.getEngineAlias()
    ));
    if (e instanceof NotFoundException || e instanceof ForbiddenException) {
      errorMessageBuilder.append(" Make sure all required engine authorizations exist");
    } else if (e instanceof NotAuthorizedException) {
      errorMessageBuilder.append(" Make sure you have configured an authorized user");
    }
    final String msg = errorMessageBuilder.toString();
    logger.error(msg, e);
  }

}
