/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByDefinitionAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.cache.ProcessCache;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class IncidentProcessInstanceStatisticsByDefinitionDocumentReader extends DocumentBasedReader
    implements IncidentProcessInstanceStatisticsByDefinitionReader {

  private final ProcessCache processCache;

  public IncidentProcessInstanceStatisticsByDefinitionDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptor indexDescriptor,
      final ProcessCache processCache) {
    super(executor, indexDescriptor);
    this.processCache = processCache;
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity> aggregate(
      final IncidentProcessInstanceStatisticsByDefinitionQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var paginatedResult =
        getSearchExecutor()
            .aggregateWithQueryResult(
                query,
                IncidentProcessInstanceStatisticsByDefinitionAggregationResult.class,
                resourceAccessChecks,
                IncidentProcessInstanceStatisticsByDefinitionAggregationResult::items);

    return new SearchQueryResult<>(
        paginatedResult.total(),
        paginatedResult.hasMoreTotalItems(),
        paginatedResult.items(),
        paginatedResult.startCursor(),
        paginatedResult.endCursor());
  }

  private List<IncidentProcessInstanceStatisticsByDefinitionEntity> enrichWithProcessDefinitionData(
      final List<IncidentProcessInstanceStatisticsByDefinitionEntity> items) {

    if (items == null || items.isEmpty()) {
      return items;
    }

    final Set<Long> processDefinitionKeys =
        items.stream()
            .map(IncidentProcessInstanceStatisticsByDefinitionEntity::processDefinitionKey)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    if (processDefinitionKeys.isEmpty()) {
      return items;
    }

    final var cacheResult = processCache.getCacheItems(processDefinitionKeys);

    return items.stream()
        .map(
            item -> {
              final Long key = item.processDefinitionKey();
              if (key == null) {
                return item;
              }

              final var processInfo = cacheResult.getProcessItem(key);
              if (processInfo != null && !processInfo.isEmpty()) {
                return new IncidentProcessInstanceStatisticsByDefinitionEntity(
                    processInfo.processDefinitionId(),
                    key,
                    processInfo.processName(),
                    processInfo.version(),
                    processInfo.tenantId(),
                    item.activeInstancesWithErrorCount());
              }

              return item;
            })
        .toList();
  }
}
