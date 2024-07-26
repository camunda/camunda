/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.COUNT;
import static io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.EVENT_NAME;
import static io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.GROUP;
import static io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.SOURCE;
import static io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.SOURCE_EVENT;
import static io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex.TARGET_EVENT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import io.camunda.optimize.service.db.os.OpenSearchCompositeAggregationScroller;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.AggregationDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.reader.EventSequenceCountReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.CompositeBucket;
import org.opensearch.client.opensearch._types.aggregations.SumAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

@AllArgsConstructor
@Slf4j
public class EventSequenceCountReaderOS implements EventSequenceCountReader {

  private final String indexKey;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  @Override
  public List<EventSequenceCountDto> getEventSequencesWithSourceInIncomingOrTargetInOutgoing(
      final List<EventTypeDto> incomingEvents, final List<EventTypeDto> outgoingEvents) {
    log.debug("Fetching event sequences for incoming and outgoing events");

    if (incomingEvents.isEmpty() && outgoingEvents.isEmpty()) {
      return Collections.emptyList();
    }

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(getIndexName(indexKey))
            .query(buildSequencedEventsQuery(incomingEvents, outgoingEvents))
            .size(LIST_FETCH_LIMIT);

    final SearchResponse<EventSequenceCountDto> searchResponse =
        osClient.search(
            searchRequest,
            EventSequenceCountDto.class,
            "Was not able to retrieve event sequence counts!");
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  @Override
  public List<EventCountResponseDto> getEventCountsForSearchTerm(
      final List<String> groups, final String searchTerm) {
    log.debug(
        "Fetching external event counts with searchTerm {} for groups: {}", searchTerm, groups);
    final BoolQuery.Builder query = buildCountRequestQuery(searchTerm);
    if (!CollectionUtils.isEmpty(groups)) {
      addGroupFilteringForQuery(groups, query);
    }

    final List<EventCountResponseDto> eventCountDtos = new ArrayList<>();
    OpenSearchCompositeAggregationScroller.create()
        .setClient(osClient)
        .query(query.build().toQuery())
        .index(List.of(getIndexName(indexKey)))
        .aggregations(
            Collections.singletonMap(
                COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION, createAggregationBuilder()))
        .size(0)
        .setPathToAggregation(COMPOSITE_EVENT_NAME_SOURCE_AND_GROUP_AGGREGATION)
        .setCompositeBucketConsumer(bucket -> eventCountDtos.add(extractEventCounts(bucket)))
        .consumeAllPages();
    return eventCountDtos;
  }

  @Override
  public Set<String> getIndexSuffixesForCurrentSequenceCountIndices() {
    final Map<String, Set<String>> aliases;
    try {
      aliases = osClient.getAliasesForIndexPattern(EVENT_SEQUENCE_COUNT_INDEX_PREFIX + "*");
    } catch (final IOException e) {
      final String errorMessage = "Could not retrieve the index keys for sequence count indices!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return aliases.values().stream()
        .flatMap(Collection::stream)
        .map(
            fullAliasName ->
                fullAliasName.substring(
                    fullAliasName.lastIndexOf(EVENT_SEQUENCE_COUNT_INDEX_PREFIX)
                        + EVENT_SEQUENCE_COUNT_INDEX_PREFIX.length()))
        .collect(Collectors.toSet());
  }

  @Override
  public List<EventSequenceCountDto> getEventSequencesContainingBothEventTypes(
      final EventTypeDto firstEventTypeDto, final EventTypeDto secondEventTypeDto) {
    log.debug(
        "Fetching event sequences containing both event types: [{}] and [{}]",
        firstEventTypeDto,
        secondEventTypeDto);

    final BoolQuery.Builder query =
        new Builder()
            .should(
                QueryDSL.and(
                    buildEventTypeBoolQueryForProperty(firstEventTypeDto, SOURCE_EVENT),
                    buildEventTypeBoolQueryForProperty(secondEventTypeDto, TARGET_EVENT)))
            .should(
                QueryDSL.and(buildEventTypeBoolQueryForProperty(firstEventTypeDto, TARGET_EVENT)),
                buildEventTypeBoolQueryForProperty(secondEventTypeDto, SOURCE_EVENT));

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(getIndexName(indexKey))
            .query(query.build().toQuery())
            .size(LIST_FETCH_LIMIT);

    final SearchResponse<EventSequenceCountDto> searchResponse =
        osClient.search(
            searchRequest,
            EventSequenceCountDto.class,
            "Was not able to retrieve event sequence counts for given event types!");
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  @Override
  public List<EventSequenceCountDto> getAllSequenceCounts() {
    log.debug("Fetching all event sequences for index key: {}", indexKey);
    final SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index(getIndexName(indexKey))
            .query(QueryDSL.matchAll())
            .size(LIST_FETCH_LIMIT)
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));
    final OpenSearchDocumentOperations.AggregatedResult<Hit<EventSequenceCountDto>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchBuilder, EventSequenceCountDto.class);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to retrieve event sequence counts for index key %s!", indexKey);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp).stream().toList();
  }

  private EventCountResponseDto extractEventCounts(final CompositeBucket bucket) {
    final String eventName = (bucket.key().get(EVENT_NAME_AGG)).to(String.class);
    final String source = (bucket.key().get(SOURCE_AGG)).to(String.class);
    final String group = (bucket.key().get(GROUP_AGG)).to(String.class);

    final long count = (long) bucket.aggregations().get(COUNT_AGG).sum().value();

    return EventCountResponseDto.builder()
        .group(group)
        .source(source)
        .eventName(eventName)
        .count(count)
        .build();
  }

  private Aggregation createAggregationBuilder() {
    final SumAggregation eventCountAggregation = new SumAggregation.Builder().field(COUNT).build();
    final List<Map<String, CompositeAggregationSource>> eventAndSourceAndGroupTerms =
        new ArrayList<>();
    eventAndSourceAndGroupTerms.add(
        Map.of(
            EVENT_NAME_AGG,
            AggregationDSL.compositeTermsAggregationSource(
                new TermsAggregation.Builder()
                    .field(SOURCE_EVENT + "." + EVENT_NAME)
                    .missingBucket(false)
                    .build())));
    eventAndSourceAndGroupTerms.add(
        Map.of(
            SOURCE_AGG,
            AggregationDSL.compositeTermsAggregationSource(
                new TermsAggregation.Builder()
                    .field(SOURCE_EVENT + "." + SOURCE)
                    .missingBucket(true)
                    .build())));
    eventAndSourceAndGroupTerms.add(
        Map.of(
            GROUP_AGG,
            AggregationDSL.compositeTermsAggregationSource(
                new TermsAggregation.Builder()
                    .field(SOURCE_EVENT + "." + GROUP)
                    .missingBucket(true)
                    .build())));
    final int size = configurationService.getOpenSearchConfiguration().getAggregationBucketLimit();

    final CompositeAggregation eventAndSourceAndGroupAgg =
        new CompositeAggregation.Builder().size(size).sources(eventAndSourceAndGroupTerms).build();
    return new Aggregation.Builder()
        .composite(eventAndSourceAndGroupAgg)
        .aggregations(Map.of(COUNT_AGG, eventCountAggregation._toAggregation()))
        .build();
  }

  private void addGroupFilteringForQuery(final List<String> groups, final BoolQuery.Builder query) {
    final List<String> nonNullGroups = groups.stream().filter(Objects::nonNull).toList();
    final boolean includeNull = groups.size() > nonNullGroups.size();
    final BoolQuery.Builder boolQuery = new BoolQuery.Builder();
    boolQuery.minimumShouldMatch("1");
    boolean notEmpty = false;
    if (!nonNullGroups.isEmpty()) {
      boolQuery.should(QueryDSL.stringTerms(getNestedField(SOURCE_EVENT, GROUP), nonNullGroups));
      notEmpty = true;
    }
    if (includeNull) {
      boolQuery.should(QueryDSL.not(QueryDSL.exists(getNestedField(SOURCE_EVENT, GROUP))));
      notEmpty = true;
    }
    if (notEmpty) {
      query.filter(boolQuery.build().toQuery());
    }
  }

  private BoolQuery.Builder buildCountRequestQuery(final String searchTerm) {
    final BoolQuery.Builder boolQuery = new BoolQuery.Builder();
    if (searchTerm == null) {
      return new BoolQuery.Builder();
    }

    final String lowerCaseSearchTerm = searchTerm.toLowerCase(Locale.ENGLISH);
    if (searchTerm.length() > MAX_GRAM) {
      return boolQuery
          .should(QueryDSL.prefix(getNestedField(SOURCE_EVENT, GROUP), lowerCaseSearchTerm))
          .should(QueryDSL.prefix(getNestedField(SOURCE_EVENT, SOURCE), lowerCaseSearchTerm))
          .should(QueryDSL.prefix(getNestedField(SOURCE_EVENT, EVENT_NAME), lowerCaseSearchTerm));
    }

    return boolQuery.should(
        new MultiMatchQuery.Builder()
            .fields(
                getNgramSearchField(GROUP),
                getNgramSearchField(SOURCE),
                getNgramSearchField(EVENT_NAME))
            .query(lowerCaseSearchTerm)
            .analyzer(KEYWORD_ANALYZER)
            .build()
            .toQuery());
  }

  private Query buildSequencedEventsQuery(
      final List<EventTypeDto> incomingEvents, final List<EventTypeDto> outgoingEvents) {
    final BoolQuery.Builder query = new Builder();
    incomingEvents.forEach(
        eventType -> query.should(buildEventTypeBoolQueryForProperty(eventType, SOURCE_EVENT)));
    outgoingEvents.forEach(
        eventType -> query.should(buildEventTypeBoolQueryForProperty(eventType, TARGET_EVENT)));
    return query.build().toQuery();
  }

  private Query buildEventTypeBoolQueryForProperty(
      final EventTypeDto eventTypeDto, final String propertyName) {
    final BoolQuery.Builder boolQuery = new Builder();
    getNullableFieldQuery(boolQuery, getNestedField(propertyName, GROUP), eventTypeDto.getGroup());
    getNullableFieldQuery(
        boolQuery, getNestedField(propertyName, SOURCE), eventTypeDto.getSource());
    boolQuery.must(
        QueryDSL.term(getNestedField(propertyName, EVENT_NAME), eventTypeDto.getEventName()));
    return boolQuery.build().toQuery();
  }

  private void getNullableFieldQuery(
      final BoolQuery.Builder builder, final String field, final String value) {
    if (value != null) {
      builder.must(QueryDSL.term(field, value));
      return;
    }
    builder.mustNot(QueryDSL.exists(field));
  }
}
