/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.event.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex.EVENT_NAME;
import static org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex.EVENT_TRACE;
import static org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex.GROUP;
import static org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex.SOURCE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.functionScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@AllArgsConstructor
@Slf4j
public class EventTraceStateReader {

  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<EventTraceStateDto> getEventTraceStateForTraceIds(List<String> traceIds) {
    log.debug("Fetching event trace states for trace ids {}", traceIds);

    final QueryBuilder query = termsQuery(EventTraceStateIndex.TRACE_ID, traceIds);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(getIndexName()).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch event trace states with trace ids [%s]", traceIds);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventTraceStateDto.class, objectMapper);
  }

  public List<EventTraceStateDto> getTracesContainingAtLeastOneEventFromEach(final List<EventTypeDto> startEvents,
                                                                             final List<EventTypeDto> endEvents,
                                                                             final int maxResultsSize) {
    log.debug("Fetching up to {} random event trace states containing given events", maxResultsSize);

    final BoolQueryBuilder containsStartEventQuery = createContainsAtLeastOneEventFromQuery(startEvents);
    final BoolQueryBuilder containsEndEventQuery = createContainsAtLeastOneEventFromQuery(endEvents);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(functionScoreQuery(
        boolQuery()
          .must(containsStartEventQuery)
          .must(containsEndEventQuery),
        ScoreFunctionBuilders.randomFunction()
      ))
      .size(maxResultsSize);
    SearchRequest searchRequest = new SearchRequest(getIndexName()).source(searchSourceBuilder);
    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Was not able to fetch event trace states";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventTraceStateDto.class, objectMapper);
  }

  private BoolQueryBuilder createContainsAtLeastOneEventFromQuery(final List<EventTypeDto> startEvents) {
    final BoolQueryBuilder containStartEventQuery = boolQuery();
    startEvents.forEach(startEvent -> containStartEventQuery.should(
      nestedQuery(
        EVENT_TRACE,
        boolQuery()
          .must(termQuery(getEventTraceNestedField(GROUP), startEvent.getGroup()))
          .must(termQuery(getEventTraceNestedField(SOURCE), startEvent.getSource()))
          .must(termQuery(getEventTraceNestedField(EVENT_NAME), startEvent.getEventName())),
        ScoreMode.None
      )
    ));
    return containStartEventQuery;
  }

  private String getEventTraceNestedField(final String searchFieldName) {
    return EVENT_TRACE + "." + searchFieldName;
  }

  private String getIndexName() {
    return new EventTraceStateIndex(indexKey).getIndexName();
  }

}
