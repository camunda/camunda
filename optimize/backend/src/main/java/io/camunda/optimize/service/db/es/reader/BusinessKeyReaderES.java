/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.BusinessKeyReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
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
public class BusinessKeyReaderES implements BusinessKeyReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(BusinessKeyReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  public BusinessKeyReaderES(
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
  }

  @Override
  public List<BusinessKeyDto> getBusinessKeysForProcessInstanceIds(
      final Set<String> processInstanceIds) {
    log.debug("Fetching business keys for [{}] process instances", processInstanceIds.size());

    if (processInstanceIds.isEmpty()) {
      return Collections.emptyList();
    }

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(QueryBuilders.idsQuery().addIds(processInstanceIds.toArray(new String[0])))
            .size(LIST_FETCH_LIMIT);
    final SearchRequest searchRequest =
        new SearchRequest(DatabaseConstants.BUSINESS_KEY_INDEX_NAME)
            .source(searchSourceBuilder)
            .scroll(
                timeValueSeconds(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getScrollTimeoutInSeconds()));

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      log.error("Was not able to retrieve business keys!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event business keys!", e);
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
        searchResponse,
        BusinessKeyDto.class,
        objectMapper,
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds());
  }
}
