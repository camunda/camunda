/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.AllEntitiesBasedImportPage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.TENANT_ENDPOINT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TenantFetcher extends RetryBackoffEngineEntityFetcher<TenantEngineDto> {

  // @formatter:off
  public static final GenericType<List<TenantEngineDto>> TENANT_LIST_TYPE = new GenericType<List<TenantEngineDto>>() {};
  // @formatter:on

  public TenantFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public List<TenantEngineDto> fetchTenants(AllEntitiesBasedImportPage page) {
    return fetchProcessDefinitions(page.getIndexOfFirstResult(), page.getPageSize());
  }

  private List<TenantEngineDto> fetchProcessDefinitions(long indexOfFirstResult, long maxPageSize) {
    List<TenantEngineDto> entries;
    logger.debug("Fetching tenants ...");
    long requestStart = System.currentTimeMillis();
    entries = fetchWithRetry(() -> performProcessDefinitionRequest(indexOfFirstResult, maxPageSize));
    long requestEnd = System.currentTimeMillis();
    logger.debug("Fetched [{}] tenants within [{}] ms", entries.size(), requestEnd - requestStart);
    return entries;
  }

  private List<TenantEngineDto> performProcessDefinitionRequest(long indexOfFirstResult, long maxPageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(TENANT_ENDPOINT)
      .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
      .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(TENANT_LIST_TYPE);
  }

}
