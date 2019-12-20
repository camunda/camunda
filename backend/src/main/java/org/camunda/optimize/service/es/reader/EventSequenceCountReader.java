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
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.COUNT;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.EVENT_NAME;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.GROUP;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.N_GRAM_FIELD;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.SOURCE;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.SOURCE_EVENT;
import static org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex.TARGET_EVENT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.sum;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventSequenceCountReader {

  private static final String GROUP_AGG = EventCountDto.Fields.group;
  private static final String SOURCE__AGG = EventCountDto.Fields.source;
  private static final String EVENT_NAME_AGG = EventCountDto.Fields.eventName;
  private static final String COUNT_AGG = EventCountDto.Fields.count;
  private static final String KEYWORD_ANALYZER = "keyword";
  private static final String DEFAULT_MISSING_KEY = "_MISSING_KEY_";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<EventSequenceCountDto> getEventSequencesWithSourceInIncomingOrTargetInOutgoing(
    final List<EventTypeDto> incomingEvents, final List<EventTypeDto> outgoingEvents) {
    log.debug("Fetching event sequences for incoming and outgoing events");

    if (incomingEvents.isEmpty() && outgoingEvents.isEmpty()) {
      return Collections.emptyList();
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(buildSequencedEventsQuery(incomingEvents, outgoingEvents))
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

  public List<EventCountDto> getEventCounts(final EventCountRequestDto eventCountRequestDto) {
    log.debug("Fetching event counts with filter [{}}]", eventCountRequestDto);

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(buildCountRequestQuery(eventCountRequestDto));
    searchSourceBuilder.aggregation(createAggregationBuilder());
    searchSourceBuilder.size(0);

    final SearchRequest searchRequest = new SearchRequest(EVENT_SEQUENCE_COUNT_INDEX_NAME).source(searchSourceBuilder);
    List<EventCountDto> eventCountDtos = new ArrayList<>();
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      Terms groups = searchResponse.getAggregations().get(GROUP_AGG);
      groups.getBuckets()
        .stream()
        .forEach(bucket -> eventCountDtos.addAll(extractEventCountsForGroupBucket(bucket)));
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to get event counts!", e);
    }
    return eventCountDtos;
  }

  private QueryBuilder buildSequencedEventsQuery(
    final List<EventTypeDto> incomingEvents,
    final List<EventTypeDto> outgoingEvents) {
    final BoolQueryBuilder query = boolQuery();
    incomingEvents
      .forEach(eventType -> query.should(buildEventTypeBoolQueryForProperty(eventType, SOURCE_EVENT)));
    outgoingEvents
      .forEach(eventType -> query.should(buildEventTypeBoolQueryForProperty(eventType, TARGET_EVENT)));
    return query;
  }

  private BoolQueryBuilder buildEventTypeBoolQueryForProperty(EventTypeDto eventTypeDto, String propertyName) {
    BoolQueryBuilder boolQuery = boolQuery();
    getNullableFieldQuery(boolQuery, getNestedField(propertyName, GROUP), eventTypeDto.getGroup());
    getNullableFieldQuery(boolQuery, getNestedField(propertyName, SOURCE), eventTypeDto.getSource());
    boolQuery.must(termQuery(getNestedField(propertyName, EVENT_NAME), eventTypeDto.getEventName()));
    return boolQuery;
  }

  private BoolQueryBuilder getNullableFieldQuery(BoolQueryBuilder builder, final String field, final String value) {
    if (value != null) {
      return builder.must(termQuery(field, value));
    }
    return builder.mustNot(existsQuery(field));
  }

  private AbstractQueryBuilder buildCountRequestQuery(final EventCountRequestDto eventCountRequestDto) {
    if (eventCountRequestDto.getSearchTerm() == null) {
      return matchAllQuery();
    }
    final String lowerCaseSearchTerm = eventCountRequestDto.getSearchTerm().toLowerCase();
    if (eventCountRequestDto.getSearchTerm().length() > IndexSettingsBuilder.MAX_GRAM) {
      return boolQuery()
        .should(prefixQuery(getNestedField(SOURCE_EVENT, GROUP), lowerCaseSearchTerm))
        .should(prefixQuery(getNestedField(SOURCE_EVENT, SOURCE), lowerCaseSearchTerm))
        .should(prefixQuery(getNestedField(SOURCE_EVENT, EVENT_NAME), lowerCaseSearchTerm));
    }

    return boolQuery().should(multiMatchQuery(
      lowerCaseSearchTerm,
      getNgramSearchField(GROUP),
      getNgramSearchField(SOURCE),
      getNgramSearchField(EVENT_NAME)
    ).analyzer(KEYWORD_ANALYZER));
  }

  private TermsAggregationBuilder createAggregationBuilder() {
    final SumAggregationBuilder eventCountAggregation = sum(COUNT_AGG)
      .field(COUNT);
    final TermsAggregationBuilder eventNameAggregation = terms(EVENT_NAME_AGG)
      .field(SOURCE_EVENT + "." + EVENT_NAME)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(eventCountAggregation);
    final TermsAggregationBuilder sourceAggregation = terms(SOURCE__AGG)
      .field(SOURCE_EVENT + "." + SOURCE)
      .missing(DEFAULT_MISSING_KEY)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(eventNameAggregation);
    return terms(GROUP_AGG)
      .field(SOURCE_EVENT + "." + GROUP)
      .missing(DEFAULT_MISSING_KEY)
      .size(MAX_RESPONSE_SIZE_LIMIT)
      .subAggregation(sourceAggregation);
  }

  private List<EventCountDto> extractEventCountsForGroupBucket(final Terms.Bucket groupBucket) {
    Terms sources = groupBucket.getAggregations().get(SOURCE__AGG);
    List<EventCountDto> eventCountsForGroup = new ArrayList<>();

    for (Terms.Bucket sourceBucket : sources.getBuckets()) {
      Terms eventNamesBuckets = sourceBucket.getAggregations().get(EVENT_NAME_AGG);
      eventNamesBuckets.getBuckets()
        .forEach(eventNameBucket -> eventCountsForGroup.add(
          EventCountDto.builder()
            .group(groupBucket.getKeyAsString().equals(DEFAULT_MISSING_KEY) ? null : groupBucket.getKeyAsString())
            .source(sourceBucket.getKeyAsString().equals(DEFAULT_MISSING_KEY) ? null : sourceBucket.getKeyAsString())
            .eventName(eventNameBucket.getKeyAsString())
            .count((long)((Sum) eventNameBucket.getAggregations().get(COUNT_AGG)).getValue())
            .build()));
    }
    return eventCountsForGroup;
  }

  private String getNgramSearchField(final String searchFieldName) {
    return getNestedField(SOURCE_EVENT, searchFieldName) + "." + N_GRAM_FIELD;
  }

  private String getNestedField(final String property, final String searchFieldName) {
    return property + "." + searchFieldName;
  }

}
