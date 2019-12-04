/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.index.EventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.EventIndex.N_GRAM_FIELD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.sort.SortOrder.ASC;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventReader {

  private static final String EVENT_NAME = EventDto.Fields.eventName;
  private static final String SOURCE = EventDto.Fields.source;
  private static final String GROUP = EventDto.Fields.group;
  private static final String KEYWORD_ANALYZER = "keyword";
  private static final Comparator DEFAULT_COMPARATOR = Comparator.comparing(
    EventCountDto::getGroup,
    String.CASE_INSENSITIVE_ORDER
  )
    .thenComparing(EventCountDto::getSource, String.CASE_INSENSITIVE_ORDER)
    .thenComparing(EventCountDto::getEventName, String.CASE_INSENSITIVE_ORDER);

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

  public List<EventCountDto> getEventCounts(final EventCountRequestDto eventCountRequestDto) {
    log.debug("Fetching event counts with filter [{}}]", eventCountRequestDto);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(buildQuery(eventCountRequestDto));
    searchSourceBuilder.aggregation(createAggregationBuilder());
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(EVENT_INDEX_NAME).source(searchSourceBuilder);
    List<EventCountDto> eventCountDtos = new ArrayList<>();
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      Terms groups = searchResponse.getAggregations().get(GROUP);
      groups.getBuckets()
        .stream()
        .forEach(bucket -> eventCountDtos.addAll(extractEventCountsForBucket(bucket)));
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to get event counts!", e);
    }
    return sortEventCountsUsingWithRequestParameters(eventCountRequestDto, eventCountDtos);
  }

  private List<EventDto> getPageOfEventsSortedByIngestionTimestamp(final QueryBuilder query, final int limit) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(SortBuilders.fieldSort(EventIndex.INGESTION_TIMESTAMP).order(ASC))
      .size(limit);

    final SearchRequest searchRequest = new SearchRequest(EVENT_INDEX_NAME)
      .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchHelper.mapHits(searchResponse.getHits(), EventDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve ingested events!", e);
    }
  }

  private AbstractQueryBuilder buildQuery(final EventCountRequestDto eventCountRequestDto) {
    if (eventCountRequestDto.getSearchTerm() == null) {
      return matchAllQuery();
    }
    final String lowerCaseSearchTerm = eventCountRequestDto.getSearchTerm().toLowerCase();
    if (eventCountRequestDto.getSearchTerm().length() > IndexSettingsBuilder.MAX_GRAM) {
      return boolQuery()
        .should(prefixQuery(GROUP, lowerCaseSearchTerm))
        .should(prefixQuery(SOURCE, lowerCaseSearchTerm))
        .should(prefixQuery(EVENT_NAME, lowerCaseSearchTerm));
    }

    return boolQuery().should(multiMatchQuery(
      lowerCaseSearchTerm,
      getNgramSearchField(GROUP),
      getNgramSearchField(SOURCE),
      getNgramSearchField(EVENT_NAME)
    ).analyzer(KEYWORD_ANALYZER));
  }

  private String getNgramSearchField(final String searchFieldName) {
    return searchFieldName + "." + N_GRAM_FIELD;
  }

  private List<EventCountDto> extractEventCountsForBucket(final Bucket bucket) {
    Terms sources = bucket.getAggregations().get(SOURCE);
    List<EventCountDto> eventCountsForGroup = new ArrayList<>();

    for (Bucket source : sources.getBuckets()) {
      Terms eventNames = source.getAggregations().get(EVENT_NAME);
      eventNames.getBuckets().stream()
        .forEach(eventName -> eventCountsForGroup.add(
          EventCountDto.builder()
            .group(bucket.getKeyAsString())
            .source(source.getKeyAsString())
            .eventName(eventName.getKeyAsString())
            .count(((Bucket) eventName).getDocCount())
            .build()));
    }
    return eventCountsForGroup;
  }

  private TermsAggregationBuilder createAggregationBuilder() {
    final TermsAggregationBuilder eventNameAggregation = terms(EVENT_NAME)
      .field(EVENT_NAME)
      .size(MAX_RESPONSE_SIZE_LIMIT);
    final TermsAggregationBuilder sourceAggregation = terms(SOURCE)
      .field(SOURCE)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(eventNameAggregation);
    return terms(GROUP)
      .field(GROUP)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(sourceAggregation);
  }

  private List<EventCountDto> sortEventCountsUsingWithRequestParameters(final EventCountRequestDto eventCountRequestDto,
                                                                        List<EventCountDto> eventCountDtos) {
    SortOrder sortOrder = eventCountRequestDto.getSortOrder();
    boolean isAscending = sortOrder == null || sortOrder.equals(SortOrder.ASC);

    Comparator comparator = Optional.ofNullable(eventCountRequestDto.getOrderBy())
      .map(orderBy ->
             sortOrderedComparator(isAscending, getCustomComparator(orderBy))
               .thenComparing(sortOrderedComparator(isAscending, DEFAULT_COMPARATOR)))
      .orElseGet(() -> sortOrderedComparator(isAscending, DEFAULT_COMPARATOR));
    eventCountDtos.sort(comparator);
    return eventCountDtos;
  }

  private Comparator sortOrderedComparator(final boolean isAscending, final Comparator comparator) {
    return isAscending ? comparator : comparator.reversed();
  }

  private Comparator getCustomComparator(final String orderBy) {
    if (orderBy.equalsIgnoreCase(EventCountDto.Fields.group)) {
      return Comparator.comparing(EventCountDto::getGroup, String.CASE_INSENSITIVE_ORDER);
    } else if (orderBy.equalsIgnoreCase(EventCountDto.Fields.source)) {
      return Comparator.comparing(EventCountDto::getSource, String.CASE_INSENSITIVE_ORDER);
    } else if (orderBy.equalsIgnoreCase(EventCountDto.Fields.eventName)) {
      return Comparator.comparing(EventCountDto::getEventName, String.CASE_INSENSITIVE_ORDER);
    } else {
      throw new OptimizeValidationException("invalid orderBy field");
    }
  }

}
