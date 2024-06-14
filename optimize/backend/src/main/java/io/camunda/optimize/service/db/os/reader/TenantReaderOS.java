/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;

import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.reader.TenantReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class TenantReaderOS implements TenantReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Set<TenantDto> getTenants() {
    log.debug("Fetching all available tenants");

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(TENANT_INDEX_NAME)
            .size(LIST_FETCH_LIMIT)
            .query(QueryDSL.matchAll())
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    OpenSearchDocumentOperations.AggregatedResult<Hit<TenantDto>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchRequest, TenantDto.class);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve tenants!", e);
    }
    return new HashSet<>(OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp));
  }
}
