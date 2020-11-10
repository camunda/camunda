/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.EventResponseDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExternalEventReader {

  private static final String MIN_AGG = "min";
  private static final String MAX_AGG = "max";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<EventResponseDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    log.debug("Fetching events that where ingested after {}", ingestTimestamp);

    final RangeQueryBuilder timestampQuery = rangeQuery(EventIndex.INGESTION_TIMESTAMP).gt(ingestTimestamp);

    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, limit);
  }

  public List<EventResponseDto> getEventsIngestedAt(final Long ingestTimestamp) {
    log.debug("Fetching events that where ingested at {}", ingestTimestamp);

    final RangeQueryBuilder timestampQuery = rangeQuery(EventIndex.INGESTION_TIMESTAMP)
      .lte(ingestTimestamp)
      .gte(ingestTimestamp);

    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps() {
    log.debug("Fetching min and max timestamp for ingested external events");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .fetchSource(false)
      .aggregation(AggregationBuilders.min(MIN_AGG).field(EventIndex.INGESTION_TIMESTAMP))
      .aggregation(AggregationBuilders.max(MAX_AGG).field(EventIndex.INGESTION_TIMESTAMP))
      .size(0);

    try {
      final SearchResponse searchResponse = esClient.search(
        new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME)
          .source(searchSourceBuilder), RequestOptions.DEFAULT);
      return ImmutablePair.of(
        extractTimestampForAggregation(searchResponse.getAggregations().get(MIN_AGG)),
        extractTimestampForAggregation(searchResponse.getAggregations().get(MAX_AGG))
      );
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve min and max event ingestion timestamps!", e);
    }
  }

  private Optional<OffsetDateTime> extractTimestampForAggregation(ParsedSingleValueNumericMetricsAggregation aggregation) {
    try {
      return Optional.of(OffsetDateTime.ofInstant(Instant.parse(aggregation.getValueAsString()), ZoneId.systemDefault()));
    } catch (DateTimeParseException ex) {
      log.warn("Could not find the {} external event ingestion timestamp.", aggregation.getType());
      return Optional.empty();
    }
  }

  private List<EventResponseDto> getPageOfEventsSortedByIngestionTimestamp(final QueryBuilder query, final int limit) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(SortBuilders.fieldSort(EventIndex.INGESTION_TIMESTAMP).order(ASC))
      .size(limit);

    final SearchRequest searchRequest = new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME)
      .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventResponseDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve ingested events!", e);
    }
  }

}
