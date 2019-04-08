/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionFetcher extends RetryBackoffEngineEntityFetcher<DecisionDefinitionEngineDto> {

  @Autowired
  public DecisionDefinitionFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public List<DecisionDefinitionEngineDto> fetchDecisionDefinitions(final AllEntitiesBasedImportPage page) {
    return fetchDecisionDefinitions(page.getIndexOfFirstResult(), page.getPageSize());
  }

  private List<DecisionDefinitionEngineDto> fetchDecisionDefinitions(final long indexOfFirstResult,
                                                                     final long maxPageSize) {
    logger.debug("Fetching decision definitions ...");
    final long requestStart = System.currentTimeMillis();
    final List<DecisionDefinitionEngineDto> entries = fetchWithRetry(
      () -> performGetDecisionDefinitionsRequest(indexOfFirstResult, maxPageSize)
    );
    final long requestEnd = System.currentTimeMillis();
    logger.debug("Fetched [{}] decision definitions within [{}] ms", entries.size(), requestEnd - requestStart);
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
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<DecisionDefinitionEngineDto>>() {});
    // @formatter:on
  }

}
