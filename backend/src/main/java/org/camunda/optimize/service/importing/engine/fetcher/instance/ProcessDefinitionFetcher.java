/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.page.AllEntitiesBasedImportPage;
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
public class ProcessDefinitionFetcher extends RetryBackoffEngineEntityFetcher<ProcessDefinitionEngineDto> {

  public ProcessDefinitionFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(AllEntitiesBasedImportPage page) {
    return fetchProcessDefinitions(
      page.getIndexOfFirstResult(),
      page.getPageSize()
    );
  }

  private List<ProcessDefinitionEngineDto> fetchProcessDefinitions(long indexOfFirstResult, long maxPageSize) {
    List<ProcessDefinitionEngineDto> entries;
    logger.debug("Fetching process definitions ...");
    long requestStart = System.currentTimeMillis();
    entries =
      fetchWithRetry(() -> performProcessDefinitionRequest(indexOfFirstResult, maxPageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] process definitions within [{}] ms",
      entries.size(),
      requestEnd - requestStart
    );


    return entries;
  }

  private List<ProcessDefinitionEngineDto> performProcessDefinitionRequest(long indexOfFirstResult, long maxPageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(configurationService.getProcessDefinitionEndpoint())
      .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
      .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(new GenericType<List<ProcessDefinitionEngineDto>>() {
      });
  }

}
