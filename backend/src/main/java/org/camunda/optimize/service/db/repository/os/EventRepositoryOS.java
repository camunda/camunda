/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import org.camunda.optimize.service.db.os.schema.index.events.CamundaActivityEventIndexOS;
import org.camunda.optimize.service.db.repository.EventRepository;
import org.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class EventRepositoryOS implements EventRepository {
  private final OptimizeOpenSearchClient osClient;
  private final DateTimeFormatter formatter;

  @Override
  public void deleteByProcessInstanceIds(
      final String definitionKey, final List<String> processInstanceIds) {
    final Query filterQuery =
        QueryDSL.stringTerms(CamundaActivityEventIndex.PROCESS_INSTANCE_ID, processInstanceIds);
    osClient.deleteByQueryTask(
        String.format("camunda activity events of %d process instances", processInstanceIds.size()),
        filterQuery,
        false,
        // use wildcarded index name to catch all indices that exist after potential rollover
        osClient
            .getIndexNameService()
            .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                new CamundaActivityEventIndexOS(definitionKey)));
  }

  @Override
  public List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(
      final String definitionKey,
      final Pair<Long, Long> timestampRange,
      final int limit,
      final TimeRangeRequest mode) {
    final Query timestampQuery;
    if (mode.equals(TimeRangeRequest.AT)) {
      timestampQuery =
          QueryDSL.gteLte(
              CamundaActivityEventIndex.TIMESTAMP,
              formatter.format(convertToOffsetDateTime(timestampRange.getLeft())),
              formatter.format(convertToOffsetDateTime(timestampRange.getRight())));
    } else if (mode.equals(TimeRangeRequest.AFTER)) {
      timestampQuery =
          QueryDSL.gt(
              CamundaActivityEventIndex.TIMESTAMP,
              formatter.format(convertToOffsetDateTime(timestampRange.getLeft())));
    } else {
      timestampQuery =
          QueryDSL.gtLt(
              CamundaActivityEventIndex.TIMESTAMP,
              formatter.format(convertToOffsetDateTime(timestampRange.getLeft())),
              formatter.format(convertToOffsetDateTime(timestampRange.getRight())));
    }

    final SearchRequest.Builder searchRequest =
        RequestDSL.searchRequestBuilder(CamundaActivityEventIndex.constructIndexName(definitionKey))
            .query(timestampQuery)
            .sort(
                new SortOptions.Builder()
                    .field(
                        new FieldSort.Builder()
                            .field(CamundaActivityEventIndex.TIMESTAMP)
                            .order(SortOrder.Asc)
                            .build())
                    .build())
            .size(limit);

    return osClient
        .getRichOpenSearchClient()
        .doc()
        .searchValues(searchRequest, CamundaActivityEventDto.class);
  }

  @Override
  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForDefinition(final String processDefinitionKey) {
    log.debug("Fetching min and max timestamp for ingested camunda events");
    final SearchRequest.Builder request =
        RequestDSL.searchRequestBuilder(
                CamundaActivityEventIndex.constructIndexName(processDefinitionKey))
            .query(QueryDSL.matchAll())
            .aggregations(
                MIN_AGG,
                AggregationBuilders.min()
                    .field(CamundaActivityEventIndex.TIMESTAMP)
                    .format(OPTIMIZE_DATE_FORMAT)
                    .build()
                    ._toAggregation())
            .aggregations(
                MAX_AGG,
                AggregationBuilders.max()
                    .field(CamundaActivityEventIndex.TIMESTAMP)
                    .format(OPTIMIZE_DATE_FORMAT)
                    .build()
                    ._toAggregation())
            .size(0);

    final String indexName = CamundaActivityEventIndex.constructIndexName(processDefinitionKey);
    final boolean indexExists = osClient.getRichOpenSearchClient().index().indexExists(indexName);
    if (indexExists) {
      final Map<String, Aggregate> searchResponse =
          osClient.getRichOpenSearchClient().doc().searchAggregations(request);
      return ImmutablePair.of(
          extractTimestampForAggregation(searchResponse.get(MIN_AGG).min().toString()),
          extractTimestampForAggregation(searchResponse.get(MAX_AGG).max().toString()));
    } else {
      log.debug("{} Index does not exist", indexName);
      return ImmutablePair.of(Optional.empty(), Optional.empty());
    }
  }

  private Optional<OffsetDateTime> extractTimestampForAggregation(final String timestamp) {
    try {
      return Optional.of(convertToOffsetDateTime(Long.valueOf(timestamp)));
    } catch (final DateTimeParseException ex) {
      log.warn("Could not find the {} camunda activity ingestion timestamp.", timestamp);
      return Optional.empty();
    }
  }
}
