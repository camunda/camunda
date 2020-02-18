/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionFetcher extends RetryBackoffEngineEntityFetcher<DecisionDefinitionEngineDto> {

  public DecisionDefinitionFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public List<DecisionDefinitionEngineDto> fetchDecisionDefinitions() {
    logger.debug("Fetching decision definitions ...");
    long requestStart = System.currentTimeMillis();
    List<DecisionDefinitionEngineDto> entries = new ArrayList<>();
    final int maxPageSize = configurationService.getEngineImportDecisionDefinitionMaxPageSize();
    long indexOfFirstResult = 0L;
    List<DecisionDefinitionEngineDto> pageOfEntries;
    do {
      final long tempIndex = indexOfFirstResult;
      pageOfEntries = fetchWithRetry(() -> performGetDecisionDefinitionsRequest(tempIndex, maxPageSize));
      entries.addAll(pageOfEntries);
      indexOfFirstResult += pageOfEntries.size();
    } while (pageOfEntries.size() >= maxPageSize);
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] decision definitions within [{}] ms",
      entries.size(),
      requestEnd - requestStart
    );
    return entries;
  }

  private List<DecisionDefinitionEngineDto> performGetDecisionDefinitionsRequest(final long indexOfFirstResult,
                                                                                 final long maxPageSize) {
    // @formatter:off
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getDecisionDefinitionEndpoint())
      .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
      .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
      .queryParam("sortBy", "id")
      .queryParam("sortOrder", "asc")
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<DecisionDefinitionEngineDto>>() {});
    // @formatter:on
  }

}
