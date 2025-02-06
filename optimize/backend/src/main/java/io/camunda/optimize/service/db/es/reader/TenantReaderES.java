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

import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.TenantReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TenantReaderES implements TenantReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TenantReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public TenantReaderES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TenantDto> getTenants() {
    LOG.debug("Fetching all available tenants");

    final SearchResponse<TenantDto> scrollResp;
    try {
      scrollResp =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(esClient, TENANT_INDEX_NAME)
                          .query(q -> q.matchAll(m -> m))
                          .size(LIST_FETCH_LIMIT)
                          .scroll(
                              ss ->
                                  ss.time(
                                      configurationService
                                              .getElasticSearchConfiguration()
                                              .getScrollTimeoutInSeconds()
                                          + "s"))),
              TenantDto.class);
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
