/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.EVENT_NAME;
import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.EVENT_TRACE;
import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.GROUP;
import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.SOURCE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.functionScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.EventTraceStateReader;
import io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

@AllArgsConstructor
@Slf4j
public class EventTraceStateReaderES implements EventTraceStateReader {

  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public List<EventTraceStateDto> getEventTraceStateForTraceIds(List<String> traceIds) {
    log.debug("Fetching event trace states for trace ids {}", traceIds);

    final QueryBuilder query = termsQuery(EventTraceStateIndex.TRACE_ID, traceIds);
    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(getIndexName(indexKey)).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason =
          String.format("Was not able to fetch event trace states with trace ids [%s]", traceIds);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), EventTraceStateDto.class, objectMapper);
  }

  @Override
  public List<EventTraceStateDto> getTracesContainingAtLeastOneEventFromEach(
      final List<EventTypeDto> startEvents,
      final List<EventTypeDto> endEvents,
      final int maxResultsSize) {
    log.debug(
        "Fetching up to {} random event trace states containing given events", maxResultsSize);

    final BoolQueryBuilder containsStartEventQuery =
        createContainsAtLeastOneEventFromQuery(startEvents);
    final BoolQueryBuilder containsEndEventQuery =
        createContainsAtLeastOneEventFromQuery(endEvents);
    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                functionScoreQuery(
                    boolQuery().must(containsStartEventQuery).must(containsEndEventQuery),
                    ScoreFunctionBuilders.randomFunction()))
            .size(maxResultsSize);
    SearchRequest searchRequest =
        new SearchRequest(getIndexName(indexKey)).source(searchSourceBuilder);
    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = "Was not able to fetch event trace states";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), EventTraceStateDto.class, objectMapper);
  }

  @Override
  public List<EventTraceStateDto> getTracesWithTraceIdIn(final List<String> traceIds) {
    log.debug("Fetching trace states with trace ID in [{}]", traceIds);

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(boolQuery().must(termsQuery(EventTraceStateIndex.TRACE_ID, traceIds)))
            .size(MAX_RESPONSE_SIZE_LIMIT);
    SearchRequest searchRequest =
        new SearchRequest(getIndexName(indexKey)).source(searchSourceBuilder);
    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = "Was not able to fetch event trace states for given trace IDs";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), EventTraceStateDto.class, objectMapper);
  }

  private BoolQueryBuilder createContainsAtLeastOneEventFromQuery(
      final List<EventTypeDto> startEvents) {
    final BoolQueryBuilder containStartEventQuery = boolQuery();
    startEvents.forEach(
        startEvent -> {
          final BoolQueryBuilder containsEventQuery =
              boolQuery()
                  .must(termQuery(getEventTraceNestedField(SOURCE), startEvent.getSource()))
                  .must(termQuery(getEventTraceNestedField(EVENT_NAME), startEvent.getEventName()));
          if (startEvent.getGroup() == null) {
            containsEventQuery.mustNot(existsQuery(getEventTraceNestedField(GROUP)));
          } else {
            containsEventQuery.must(
                termQuery(getEventTraceNestedField(GROUP), startEvent.getGroup()));
          }
          containStartEventQuery.should(
              nestedQuery(EVENT_TRACE, containsEventQuery, ScoreMode.None));
        });
    return containStartEventQuery;
  }
}
