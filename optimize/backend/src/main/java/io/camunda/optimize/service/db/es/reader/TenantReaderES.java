/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.TenantReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TenantReaderES implements TenantReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(TenantReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public TenantReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TenantDto> getTenants() {
    log.debug("Fetching all available tenants");

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(LIST_FETCH_LIMIT);
    final SearchRequest searchRequest =
        new SearchRequest(TENANT_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve tenants!", e);
    }

    return new HashSet<>(
        ElasticsearchReaderUtil.retrieveAllScrollResults(
            scrollResp,
            TenantDto.class,
            objectMapper,
            esClient,
            configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds()));
  }
}
