/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.service.es.reader.ReportReader.REPORT_DATA_XML_PROPERTY;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.OWNER;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@RequiredArgsConstructor
@Component
@Slf4j
public class EntitiesReader {

  private static final String[] ENTITY_LIST_EXCLUDES = {REPORT_DATA_XML_PROPERTY};

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;


  public List<CollectionEntity> getAllPrivateEntities(String userId) {
    log.debug("Fetching all available private entities for user");

    final QueryBuilder query = boolQuery().must(termQuery(OWNER, userId))
      .mustNot(existsQuery(COLLECTION_ID));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, ENTITY_LIST_EXCLUDES);
    SearchRequest searchRequest =
      new SearchRequest(
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME,
        COMBINED_REPORT_INDEX_NAME,
        DASHBOARD_INDEX_NAME
      )
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve private entities!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve private entities!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      CollectionEntity.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }
}
