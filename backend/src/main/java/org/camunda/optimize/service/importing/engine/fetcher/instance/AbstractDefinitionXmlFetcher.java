/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractDefinitionXmlFetcher<T> extends RetryBackoffEngineEntityFetcher {

  protected AbstractDefinitionXmlFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  protected abstract void markDefinitionAsDeleted(final String definitionId);

  protected abstract String getRequestPath();

  protected abstract Class<T> getOptimizeClassForDefinitionResponse();

  public List<T> fetchXmlsForDefinitions(IdSetBasedImportPage page) {
    logger.debug("Fetching definition xml ...");
    final long requestStart = System.currentTimeMillis();
    final List<T> xmls = page.getIds().stream()
      .distinct()
      .map(this::fetchWithRetryIgnoreClientError)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
    final long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] definition xmls within [{}] ms", xmls.size(), requestEnd - requestStart
    );
    return xmls;
  }

  private Optional<T> fetchWithRetryIgnoreClientError(String definitionId) {
    T result;
    try {
      result = getDefinitionForId(definitionId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OptimizeRuntimeException("Was interrupted while fetching.", e);
    }
    backoffCalculator.resetBackoff();
    return Optional.ofNullable(result);
  }

  private T getDefinitionForId(final String definitionId) throws InterruptedException {
    T result = null;
    boolean fetched = false;
    while (!fetched) {
      try {
        result = performGetDefinitionXmlRequest(definitionId);
        fetched = true;
      } catch (ClientErrorException ex) {
        // We get a 404 if the definition has been deleted. In this case, we mark the definition as deleted in Optimize
        if (ex.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
          markDefinitionAsDeleted(definitionId);
        }
        logger.warn("ClientError on fetching entity: {}", ex.getMessage(), ex);
        fetched = true;
      } catch (IllegalStateException e) {
        throw e;
      } catch (Exception ex) {
        logError(ex);
        long timeToSleep = backoffCalculator.calculateSleepTime();
        logDebugSleepInformation(timeToSleep);
        Thread.sleep(timeToSleep);
      }
    }
    return result;
  }

  private T performGetDefinitionXmlRequest(final String definitionId) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(getRequestPath())
      .resolveTemplate("id", definitionId)
      .request(MediaType.APPLICATION_JSON)
      .get(getOptimizeClassForDefinitionResponse());
  }

}
