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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto;
import org.camunda.optimize.service.es.CompositeAggregationScroller;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.sorting.SortOrder.DESC;
import static org.camunda.optimize.service.es.schema.index.events.EventIndex.EVENT_NAME;
import static org.camunda.optimize.service.es.schema.index.events.EventIndex.GROUP;
import static org.camunda.optimize.service.es.schema.index.events.EventIndex.INGESTION_TIMESTAMP;
import static org.camunda.optimize.service.es.schema.index.events.EventIndex.N_GRAM_FIELD;
import static org.camunda.optimize.service.es.schema.index.events.EventIndex.SOURCE;
import static org.camunda.optimize.service.es.schema.index.events.EventIndex.TIMESTAMP;
import static org.camunda.optimize.service.es.schema.index.events.EventIndex.TRACE_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_NULLS_FIRST;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_NULLS_LAST;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.prefixQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExternalEventReader {

  private static final String MIN_AGG = "min";
  private static final String MAX_AGG = "max";
  private static final String KEYWORD_ANALYZER = "keyword";

  private static final Map<String, String> sortableFieldLookup = ImmutableMap.of(
    EventDto.Fields.group.toLowerCase(), GROUP,
    EventDto.Fields.source.toLowerCase(), SOURCE,
    EventDto.Fields.eventName.toLowerCase(), EVENT_NAME,
    EventDto.Fields.traceId.toLowerCase(), TRACE_ID,
    EventDto.Fields.timestamp.toLowerCase(), TIMESTAMP
  );

  private static final String EVENT_GROUP_AGG = "eventGroupAggregation";
  private static final String LOWERCASE_GROUP_AGG = "lowercaseGroupAggregation";
  private static final String GROUP_COMPOSITE_AGG = "compositeAggregation";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    log.debug("Fetching events that where ingested after {}", ingestTimestamp);

    final RangeQueryBuilder timestampQuery = rangeQuery(INGESTION_TIMESTAMP).gt(ingestTimestamp);

    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, limit);
  }

  public List<EventDto> getEventsIngestedAfterForGroups(final Long ingestTimestamp, final int limit,
                                                        final List<String> groups) {
    log.debug("Fetching events that where ingested after {} for groups {}", ingestTimestamp, groups);

    final BoolQueryBuilder query = buildGroupFilterQuery(groups)
      .must(rangeQuery(INGESTION_TIMESTAMP).gt(ingestTimestamp));

    return getPageOfEventsSortedByIngestionTimestamp(query, limit);
  }

  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    log.debug("Fetching events that where ingested at {}", ingestTimestamp);

    final RangeQueryBuilder timestampQuery = rangeQuery(INGESTION_TIMESTAMP)
      .lte(ingestTimestamp)
      .gte(ingestTimestamp);

    return getPageOfEventsSortedByIngestionTimestamp(timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  public List<EventDto> getEventsIngestedAtForGroups(final Long ingestTimestamp, final List<String> groups) {
    log.debug("Fetching events that where ingested at {} for groups {}", ingestTimestamp, groups);

    final BoolQueryBuilder query = buildGroupFilterQuery(groups)
      .must(rangeQuery(INGESTION_TIMESTAMP)
              .lte(ingestTimestamp)
              .gte(ingestTimestamp));

    return getPageOfEventsSortedByIngestionTimestamp(query, MAX_RESPONSE_SIZE_LIMIT);
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps() {
    log.debug("Fetching min and max timestamp for ingested external events");
    return getMinAndMaxIngestedTimestampsForQuery(matchAllQuery());
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForGroups(
    final List<String> groups) {
    log.debug("Fetching min and max timestamp for ingested external events in groups: {}", groups);
    return getMinAndMaxIngestedTimestampsForQuery(buildGroupFilterQuery(groups));
  }

  private BoolQueryBuilder buildGroupFilterQuery(final List<String> groups) {
    final BoolQueryBuilder groupsQuery = boolQuery();
    final List<String> nonNullGroups = groups.stream()
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    final boolean includeNull = groups.size() > nonNullGroups.size();
    final BoolQueryBuilder groupFilterQuery = boolQuery().minimumShouldMatch(1);
    if (!nonNullGroups.isEmpty()) {
      groupFilterQuery.should(termsQuery(GROUP, nonNullGroups));
    }
    if (includeNull) {
      groupFilterQuery.should(boolQuery().mustNot(existsQuery(GROUP)));
    }
    if (!CollectionUtils.isEmpty(groupFilterQuery.should())) {
      groupsQuery.filter().add(groupFilterQuery);
    }
    return groupsQuery;
  }

  private Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForQuery(AbstractQueryBuilder<?> query) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(AggregationBuilders.min(MIN_AGG).field(INGESTION_TIMESTAMP))
      .aggregation(AggregationBuilders.max(MAX_AGG).field(INGESTION_TIMESTAMP))
      .size(0);

    try {
      final SearchResponse searchResponse = esClient.search(
        new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME).source(searchSourceBuilder));
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
    searchSourceBuilder.sort(SortBuilders.fieldSort(TIMESTAMP).order(SortOrder.DESC));


    final SearchRequest searchRequest = new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME)
      .source(searchSourceBuilder);
    try {
      return toPage(eventSearchRequestDto, esClient.search(searchRequest));
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve events!", e);
    }
  }

  public List<String> getEventGroups(final EventGroupRequestDto eventGroupRequestDto) {
    log.debug("Fetching event groups using search criteria {}", eventGroupRequestDto);

    final String searchTerm = eventGroupRequestDto.getSearchTerm();
    AbstractQueryBuilder<?> query;
    if (searchTerm == null) {
      query = matchAllQuery();
    } else if (searchTerm.length() > IndexSettingsBuilder.MAX_GRAM) {
      query = boolQuery().must(prefixQuery(GROUP, searchTerm));
    } else {
      query = boolQuery()
        .must(matchQuery(getNgramSearchField(GROUP), searchTerm.toLowerCase()).analyzer(KEYWORD_ANALYZER));
    }

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .aggregation(buildCompositeGroupAggregation(eventGroupRequestDto))
      .size(0);

    final SearchRequest searchRequest = new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME)
      .source(searchSourceBuilder);
    List<String> groups = new ArrayList<>();
    CompositeAggregationScroller.create()
      .setEsClient(esClient)
      .setSearchRequest(searchRequest)
      .setPathToAggregation(GROUP_COMPOSITE_AGG)
      .setCompositeBucketConsumer(bucket -> groups.add((String) (bucket.getKey().get(EVENT_GROUP_AGG))))
      .consumePage();
    return groups;
  }

  private CompositeAggregationBuilder buildCompositeGroupAggregation(final EventGroupRequestDto eventGroupRequestDto) {
    // We aggregate on the group name to return to user and the lower case name so we can sort properly
    List<CompositeValuesSourceBuilder<?>> eventGroupsAndLowercaseGroups = new ArrayList<>();
    eventGroupsAndLowercaseGroups.add(
      new TermsValuesSourceBuilder(LOWERCASE_GROUP_AGG)
        .field(GROUP + "." + DefaultIndexMappingCreator.LOWERCASE)
        .order(ASC)
        .missingBucket(true));
    eventGroupsAndLowercaseGroups.add
      (new TermsValuesSourceBuilder(EVENT_GROUP_AGG)
         .field(GROUP)
         .order(ASC)
         .missingBucket(true));
    return new CompositeAggregationBuilder(GROUP_COMPOSITE_AGG, eventGroupsAndLowercaseGroups)
      .size(Math.min(
        eventGroupRequestDto.getLimit(),
        configurationService.getEsAggregationBucketLimit()
      ));
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
        .should(prefixQuery(GROUP, searchTerm))
        .should(prefixQuery(SOURCE, searchTerm))
        .should(prefixQuery(EVENT_NAME, searchTerm))
        .should(prefixQuery(TRACE_ID, searchTerm));
    }

    return boolQuery().should(QueryBuilders.multiMatchQuery(
      searchTerm.toLowerCase(),
      getNgramSearchField(GROUP),
      getNgramSearchField(SOURCE),
      getNgramSearchField(EVENT_NAME),
      getNgramSearchField(TRACE_ID)
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

  private List<EventDto> getPageOfEventsSortedByIngestionTimestamp(final AbstractQueryBuilder<?> query,
                                                                   final int limit) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(SortBuilders.fieldSort(INGESTION_TIMESTAMP).order(ASC))
      .size(limit);

    final SearchRequest searchRequest = new SearchRequest(EXTERNAL_EVENTS_INDEX_NAME)
      .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), EventDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve ingested events by timestamp!", e);
    }
  }

}
