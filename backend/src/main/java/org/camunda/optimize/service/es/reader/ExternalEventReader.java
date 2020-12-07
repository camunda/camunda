/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC;
import static org.camunda.optimize.service.es.schema.index.events.EventIndex.N_GRAM_FIELD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_NULLS_FIRST;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_NULLS_LAST;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExternalEventReader {

  private static final String MIN_AGG = "min";
  private static final String MAX_AGG = "max";
  private static final String KEYWORD_ANALYZER = "keyword";

  private static final Map<String, String> sortableFieldLookup = ImmutableMap.of(
    EventDto.Fields.group.toLowerCase(), EventIndex.GROUP,
    EventDto.Fields.source.toLowerCase(), EventIndex.SOURCE,
    EventDto.Fields.eventName.toLowerCase(), EventIndex.EVENT_NAME,
    EventDto.Fields.traceId.toLowerCase(), EventIndex.TRACE_ID,
    EventDto.Fields.timestamp.toLowerCase(), EventIndex.TIMESTAMP
  );

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    log.debug("Fetching events that where ingested after {}", ingestTimestamp);

    final RangeQueryBuilder timestampQuery = rangeQuery(EventIndex.INGESTION_TIMESTAMP).gt(ingestTimestamp);

    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, limit);
  }

  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
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

  public Page<DeletableEventDto> getEventsForRequest(final EventSearchRequestDto eventSearchRequestDto) {
    log.debug("Fetching events using search criteria {}", eventSearchRequestDto);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(getSearchQueryForEventRequest(eventSearchRequestDto))
      .from(eventSearchRequestDto.getPaginationRequestDto().getOffset())
      .size(eventSearchRequestDto.getPaginationRequestDto().getLimit());
    getSortForEventRequest(eventSearchRequestDto.getSortRequestDto()).ifPresent(searchSourceBuilder::sort);
    // add secondary sort order
    searchSourceBuilder.sort(SortBuilders.fieldSort(EventIndex.TIMESTAMP).order(SortOrder.DESC));


    final SearchRequest searchRequest = new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME)
      .source(searchSourceBuilder);
    try {
      return toPage(eventSearchRequestDto, esClient.search(searchRequest, RequestOptions.DEFAULT));
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve events!", e);
    }
  }

  private Page<DeletableEventDto> toPage(final EventSearchRequestDto eventSearchRequestDto,
                                         final SearchResponse searchResponse) {
    final List<EventDto> eventsForRequest = ElasticsearchReaderUtil.mapHits(
      searchResponse.getHits(),
      EventDto.class,
      objectMapper
    );
    return new Page<>(
      eventSearchRequestDto.getPaginationRequestDto().getOffset(),
      eventSearchRequestDto.getPaginationRequestDto().getLimit(),
      searchResponse.getHits().getTotalHits().value,
      eventSearchRequestDto.getSortRequestDto().getSortBy().orElse(DeletableEventDto.Fields.timestamp),
      eventSearchRequestDto.getSortRequestDto().getSortOrder().orElse(DESC),
      eventsForRequest.stream().map(DeletableEventDto::from).collect(Collectors.toList())
    );
  }

  private QueryBuilder getSearchQueryForEventRequest(final EventSearchRequestDto eventSearchRequestDto) {
    final String searchTerm = eventSearchRequestDto.getSearchTerm();
    if (eventSearchRequestDto.getSearchTerm() == null) {
      return matchAllQuery();
    }

    if (searchTerm.length() > IndexSettingsBuilder.MAX_GRAM) {
      return boolQuery().minimumShouldMatch(1)
        .should(prefixQuery(EventIndex.GROUP, searchTerm))
        .should(prefixQuery(EventIndex.SOURCE, searchTerm))
        .should(prefixQuery(EventIndex.EVENT_NAME, searchTerm))
        .should(prefixQuery(EventIndex.TRACE_ID, searchTerm));
    }

    return boolQuery().should(QueryBuilders.multiMatchQuery(
      searchTerm.toLowerCase(),
      getNgramSearchField(EventIndex.GROUP),
      getNgramSearchField(EventIndex.SOURCE),
      getNgramSearchField(EventIndex.EVENT_NAME),
      getNgramSearchField(EventIndex.TRACE_ID)
    ).analyzer(KEYWORD_ANALYZER));
  }

  private String getNgramSearchField(final String searchFieldName) {
    return searchFieldName + "." + N_GRAM_FIELD;
  }

  private Optional<FieldSortBuilder> getSortForEventRequest(final SortRequestDto sortRequestDto) {
    final Optional<String> sortByOpt = sortRequestDto.getSortBy();
    if (sortByOpt.isPresent()) {
      FieldSortBuilder fieldSortBuilder =
        SortBuilders.fieldSort(convertToIndexSortField(sortByOpt.get()));
      sortRequestDto.getSortOrder()
        .ifPresent(order -> {
          // This makes sure that nullable fields respect the sort order
          if (org.camunda.optimize.dto.optimize.query.sorting.SortOrder.ASC.equals(order)) {
            fieldSortBuilder.order(SortOrder.fromString(order.toString())).missing(SORT_NULLS_FIRST);
          } else {
            fieldSortBuilder.order(SortOrder.fromString(order.toString())).missing(SORT_NULLS_LAST);
          }
        });
      return Optional.of(fieldSortBuilder);
    }
    return Optional.empty();
  }

  private String convertToIndexSortField(final String providedField) {
    if (sortableFieldLookup.containsKey(providedField.toLowerCase())) {
      return sortableFieldLookup.get(providedField.toLowerCase());
    } else {
      throw new OptimizeRuntimeException("Could not extract event sort field from " + providedField);
    }
  }

  private Optional<OffsetDateTime> extractTimestampForAggregation(ParsedSingleValueNumericMetricsAggregation aggregation) {
    try {
      return Optional.of(OffsetDateTime.ofInstant(
        Instant.parse(aggregation.getValueAsString()),
        ZoneId.systemDefault()
      ));
    } catch (DateTimeParseException ex) {
      log.warn("Could not find the {} external event ingestion timestamp.", aggregation.getType());
      return Optional.empty();
    }
  }

  private List<EventDto> getPageOfEventsSortedByIngestionTimestamp(final QueryBuilder query, final int limit) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(SortBuilders.fieldSort(EventIndex.INGESTION_TIMESTAMP).order(ASC))
      .size(limit);

    final SearchRequest searchRequest = new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME)
      .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve ingested events by timestamp!", e);
    }
  }

}
