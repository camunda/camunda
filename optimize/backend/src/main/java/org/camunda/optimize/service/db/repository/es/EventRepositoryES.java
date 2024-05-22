/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.db.es.schema.index.events.CamundaActivityEventIndexES;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.db.repository.EventRepository;
import org.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.ParsedSingleValueNumericMetricsAggregation;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class EventRepositoryES implements EventRepository {
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter formatter;
  private final ObjectMapper objectMapper;

  @Override
  public void deleteByProcessInstanceIds(
      final String definitionKey, final List<String> processInstanceIds) {
    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(termsQuery(CamundaActivityEventIndex.PROCESS_INSTANCE_ID, processInstanceIds));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        String.format("camunda activity events of %d process instances", processInstanceIds.size()),
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        esClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new CamundaActivityEventIndexES(definitionKey)));
  }

  @Override
  public List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(
      final String definitionKey,
      final Pair<Long, Long> timestampRange,
      final int limit,
      final TimeRangeRequest mode) {
    final RangeQueryBuilder timestampQuery;
    if (mode.equals(TimeRangeRequest.AT)) {
      timestampQuery =
          rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
              .lte(formatter.format(convertToOffsetDateTime(timestampRange.getLeft())))
              .gte(formatter.format(convertToOffsetDateTime(timestampRange.getRight())));
    } else if (mode.equals(TimeRangeRequest.AFTER)) {
      timestampQuery =
          rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
              .gt(formatter.format(convertToOffsetDateTime(timestampRange.getLeft())));
    } else {
      timestampQuery =
          rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
              .gt(formatter.format(convertToOffsetDateTime(timestampRange.getLeft())))
              .lt(formatter.format(convertToOffsetDateTime(timestampRange.getRight())));
    }
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(timestampQuery)
            .sort(SortBuilders.fieldSort(CamundaActivityEventIndex.TIMESTAMP).order(ASC))
            .size(limit);

    final SearchRequest searchRequest =
        new SearchRequest(CamundaActivityEventIndex.constructIndexName(definitionKey))
            .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest);
      return ElasticsearchReaderUtil.mapHits(
          searchResponse.getHits(), CamundaActivityEventDto.class, objectMapper);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve camunda activity events!", e);
    }
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey) {
    log.debug("Fetching min and max timestamp for ingested camunda events");

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(matchAllQuery())
            .fetchSource(false)
            .aggregation(
                AggregationBuilders.min(MIN_AGG)
                    .field(CamundaActivityEventIndex.TIMESTAMP)
                    .format(OPTIMIZE_DATE_FORMAT))
            .aggregation(
                AggregationBuilders.max(MAX_AGG)
                    .field(CamundaActivityEventIndex.TIMESTAMP)
                    .format(OPTIMIZE_DATE_FORMAT))
            .size(0);

    try {
      final String indexName = CamundaActivityEventIndex.constructIndexName(processDefinitionKey);
      final boolean indexExists = esClient.exists(new GetIndexRequest(indexName));
      if (indexExists) {
        final SearchResponse searchResponse =
            esClient.search(new SearchRequest(indexName).source(searchSourceBuilder));
        return ImmutablePair.of(
            extractTimestampForAggregation(searchResponse.getAggregations().get(MIN_AGG)),
            extractTimestampForAggregation(searchResponse.getAggregations().get(MAX_AGG)));
      } else {
        log.debug("{} Index does not exist", indexName);
        return ImmutablePair.of(Optional.empty(), Optional.empty());
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Was not able to retrieve min and max camunda activity ingestion timestamps!", e);
    }
  }

  private Optional<OffsetDateTime> extractTimestampForAggregation(
      final ParsedSingleValueNumericMetricsAggregation aggregation) {
    try {
      return Optional.of(
          OffsetDateTime.ofInstant(
              OffsetDateTime.parse(aggregation.getValueAsString(), formatter).toInstant(),
              ZoneId.systemDefault()));
    } catch (final DateTimeParseException ex) {
      log.warn(
          "Could not find the {} camunda activity ingestion timestamp.", aggregation.getType());
      return Optional.empty();
    }
  }
}
