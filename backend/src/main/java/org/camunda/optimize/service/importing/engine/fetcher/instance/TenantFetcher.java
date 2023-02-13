/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.fetcher.instance;

import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.util.importing.EngineConstants.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.importing.EngineConstants.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.importing.EngineConstants.TENANT_ENDPOINT;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TenantFetcher extends RetryBackoffEngineEntityFetcher {

  // @formatter:off
  private static final GenericType<List<TenantEngineDto>> TENANT_LIST_TYPE = new GenericType<List<TenantEngineDto>>() {};
  // @formatter:on

  public TenantFetcher(final EngineContext engineContext) {
    super(engineContext);
  }

  public List<TenantEngineDto> fetchTenants() {
    logger.debug("Fetching tenants ...");
    long requestStart = System.currentTimeMillis();
    List<TenantEngineDto> entries = new ArrayList<>();
    final int maxPageSize = configurationService.getEngineImportTenantMaxPageSize();
    long indexOfFirstResult = 0L;
    List<TenantEngineDto> pageOfEntries;
    do {
      final long tempIndex = indexOfFirstResult;
      pageOfEntries = fetchWithRetry(() -> performTenantRequest(tempIndex, maxPageSize));
      entries.addAll(pageOfEntries);
      indexOfFirstResult += pageOfEntries.size();
    } while (pageOfEntries.size() >= maxPageSize);
    long requestEnd = System.currentTimeMillis();
    logger.debug(
      "Fetched [{}] tenants within [{}] ms",
      entries.size(),
      requestEnd - requestStart
    );
    return entries;
  }

  private List<TenantEngineDto> performTenantRequest(long indexOfFirstResult, long maxPageSize) {
    return getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(TENANT_ENDPOINT)
      .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
      .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
      .queryParam("sortBy", "id")
      .queryParam("sortOrder", "asc")
      .request(MediaType.APPLICATION_JSON)
      .acceptEncoding(UTF8)
      .get(TENANT_LIST_TYPE);
  }

}
