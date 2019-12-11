/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventSequenceCountReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<EventSequenceCountDto> getAllEventSequenceCounts() {
    log.debug("Fetching all event sequence counts");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(EVENT_SEQUENCE_COUNT_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve event sequence counts!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve event sequence counts!", e);
    }
    return ElasticsearchHelper.mapHits(searchResponse.getHits(), EventSequenceCountDto.class, objectMapper);
  }

}
