/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;

import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.reader.TenantReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class TenantReaderOS implements TenantReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TenantReaderOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  public TenantReaderOS(
      final OptimizeOpenSearchClient osClient, final ConfigurationService configurationService) {
    this.osClient = osClient;
    this.configurationService = configurationService;
  }

  @Override
  public Set<TenantDto> getTenants() {
    LOG.debug("Fetching all available tenants");

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

    final OpenSearchDocumentOperations.AggregatedResult<Hit<TenantDto>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchRequest, TenantDto.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve tenants!", e);
    }
    return new HashSet<>(OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp));
  }
}
