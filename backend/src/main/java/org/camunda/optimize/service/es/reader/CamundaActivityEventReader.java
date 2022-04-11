/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventReader {

  private static final String MIN_AGG = "min";
  private static final String MAX_AGG = "max";

  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter formatter;

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAfter(final String definitionKey,
                                                                                  final Long eventTimestamp,
                                                                                  final int limit) {
    log.debug(
      "Fetching camunda activity events for key [{}] and with timestamp after {}", definitionKey, eventTimestamp
    );

    final RangeQueryBuilder timestampQuery = rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
      .gt(formatter.format(convertToOffsetDateTime(eventTimestamp)));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, timestampQuery, limit);
  }

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAt(final String definitionKey,
                                                                               final Long eventTimestamp) {
    log.debug(
      "Fetching camunda activity events for key [{}] and with exact timestamp {}.", definitionKey, eventTimestamp
    );

    final RangeQueryBuilder timestampQuery = rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
      .lte(formatter.format(convertToOffsetDateTime(eventTimestamp)))
      .gte(formatter.format(convertToOffsetDateTime(eventTimestamp)));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionBetween(
    final String definitionKey,
    final Long startTimestamp,
    final Long endTimestamp,
    final int limit) {
    log.debug(
      "Fetching camunda activity events for key [{}] with timestamp between {} and {}",
      definitionKey, startTimestamp, endTimestamp);

    final RangeQueryBuilder eventsQuery = rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
      .gt(formatter.format(convertToOffsetDateTime(startTimestamp)))
      .lt(formatter.format(convertToOffsetDateTime(endTimestamp)));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, eventsQuery, limit);
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey) {
    log.debug("Fetching min and max timestamp for ingested camunda events");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .fetchSource(false)
      .aggregation(AggregationBuilders.min(MIN_AGG).field(CamundaActivityEventIndex.TIMESTAMP).format(OPTIMIZE_DATE_FORMAT))
      .aggregation(AggregationBuilders.max(MAX_AGG).field(CamundaActivityEventIndex.TIMESTAMP).format(OPTIMIZE_DATE_FORMAT))
      .size(0);

    try {
      String indexName = new CamundaActivityEventIndex(processDefinitionKey).getIndexName();
      boolean indexExists = esClient.exists(new GetIndexRequest(indexName));
      if (indexExists) {
        final SearchResponse searchResponse = esClient.search(
          new SearchRequest(indexName)
            .source(searchSourceBuilder));
        return ImmutablePair.of(
          extractTimestampForAggregation(searchResponse.getAggregations().get(MIN_AGG)),
          extractTimestampForAggregation(searchResponse.getAggregations().get(MAX_AGG))
        );
      } else {
        log.debug("{} Index does not exist", indexName);
        return ImmutablePair.of(Optional.empty(), Optional.empty());
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve min and max camunda activity ingestion timestamps!", e);
    }
  }

  public Set<String> getIndexSuffixesForCurrentActivityIndices() {
    final GetAliasesResponse aliases;
    try {
      aliases = esClient.getAlias(new GetAliasesRequest(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"));
    } catch (IOException e) {
      final String errorMessage = "Could not retrieve the definition keys for Camunda event imported definitions!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetadata::alias))
      .map(fullAliasName -> fullAliasName.substring(
        fullAliasName.lastIndexOf(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX) + CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX.length()
      ))
      .collect(Collectors.toSet());
  }

  private Optional<OffsetDateTime> extractTimestampForAggregation(ParsedSingleValueNumericMetricsAggregation aggregation) {
    try {
      return Optional.of(OffsetDateTime.ofInstant(
        OffsetDateTime.parse(aggregation.getValueAsString(), formatter).toInstant(),
        ZoneId.systemDefault())
      );
    } catch (DateTimeParseException ex) {
      log.warn("Could not find the {} camunda activity ingestion timestamp.", aggregation.getType());
      return Optional.empty();
    }
  }

  private OffsetDateTime convertToOffsetDateTime(final Long eventTimestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(eventTimestamp), ZoneId.systemDefault());
  }

  private List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(final String definitionKey,
                                                                                         final QueryBuilder query,
                                                                                         final int limit) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(SortBuilders.fieldSort(CamundaActivityEventIndex.TIMESTAMP).order(ASC))
      .size(limit);

    final SearchRequest searchRequest =
      new SearchRequest(new CamundaActivityEventIndex(definitionKey).getIndexName())
        .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), CamundaActivityEventDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve camunda activity events!", e);
    }
  }
}
