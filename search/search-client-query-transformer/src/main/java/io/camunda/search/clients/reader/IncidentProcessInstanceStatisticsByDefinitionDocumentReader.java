/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByDefinitionAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentProcessInstanceStatisticsByDefinitionDocumentReader extends DocumentBasedReader
    implements IncidentProcessInstanceStatisticsByDefinitionReader {

  public static final int MAXIMUM_CACHE_SIZE = 1_000;
  public static final int TTL_SECONDS = 300;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(IncidentProcessInstanceStatisticsByDefinitionDocumentReader.class);

  private final Cache<Long, CachedProcessInfo> cache =
      Caffeine.newBuilder()
          .expireAfterWrite(Duration.ofSeconds(TTL_SECONDS))
          .maximumSize(MAXIMUM_CACHE_SIZE)
          .build();

  public IncidentProcessInstanceStatisticsByDefinitionDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
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

    final var enrichedItems = enrichWithProcessDefinitionData(paginatedResult.items());

    return new SearchQueryResult<>(
        paginatedResult.total(),
        paginatedResult.hasMoreTotalItems(),
        enrichedItems,
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

    final Map<Long, CachedProcessInfo> cachedValues = cache.getAllPresent(processDefinitionKeys);
    final Set<Long> missingKeys =
        processDefinitionKeys.stream()
            .filter(key -> !cachedValues.containsKey(key))
            .collect(Collectors.toSet());

    final Map<Long, CachedProcessInfo> fetchedValues = fetchProcessDefinitions(missingKeys);
    fetchedValues.forEach(cache::put);

    return items.stream()
        .map(
            item -> {
              final Long key = item.processDefinitionKey();
              if (key == null) {
                return item;
              }

              final CachedProcessInfo processInfo =
                  cachedValues.containsKey(key)
                      ? cachedValues.get(key)
                      : cache.get(key, fetchedValues::get);
              if (processInfo != null) {
                return new IncidentProcessInstanceStatisticsByDefinitionEntity(
                    processInfo.processDefinitionId(),
                    key,
                    processInfo.processName(),
                    processInfo.version(),
                    processInfo.tenantId(),
                    item.activeInstancesWithErrorCount());
              }

              if (item.processDefinitionId() != null || item.processDefinitionName() != null) {
                final CachedProcessInfo seeded =
                    new CachedProcessInfo(
                        item.processDefinitionId(),
                        item.processDefinitionName(),
                        item.processDefinitionVersion(),
                        item.tenantId());
                cache.put(key, seeded);
                return new IncidentProcessInstanceStatisticsByDefinitionEntity(
                    seeded.processDefinitionId(),
                    key,
                    seeded.processName(),
                    seeded.version(),
                    item.tenantId(),
                    item.activeInstancesWithErrorCount());
              }

              return item;
            })
        .toList();
  }

  private Map<Long, CachedProcessInfo> fetchProcessDefinitions(
      final Set<Long> processDefinitionKeys) {
    if (processDefinitionKeys == null || processDefinitionKeys.isEmpty()) {
      return Collections.emptyMap();
    }

    try {
      final var query =
          ProcessDefinitionQuery.of(
              q ->
                  q.filter(f -> f.processDefinitionKeys(new ArrayList<>(processDefinitionKeys)))
                      .page(SearchQueryPage.of(p -> p.size(processDefinitionKeys.size())))
                      .sort(SortOptionBuilders.processDefinition().build()));

      final SearchQueryResult<ProcessDefinitionEntity> result =
          getSearchExecutor().search(query, ProcessEntity.class, ResourceAccessChecks.disabled());

      final Map<Long, CachedProcessInfo> fetched = new HashMap<>();
      result.items().stream()
          .filter(Objects::nonNull)
          .forEach(
              pd ->
                  fetched.put(
                      pd.processDefinitionKey(),
                      new CachedProcessInfo(
                          pd.processDefinitionId(), pd.name(), pd.version(), pd.tenantId())));
      return fetched;
    } catch (final Exception ex) {
      LOGGER.warn(
          "Failed to fetch process definitions for keys {}. Returning empty result.",
          processDefinitionKeys,
          ex);
      return Collections.emptyMap();
    }
  }

  private record CachedProcessInfo(
      String processDefinitionId, String processName, Integer version, String tenantId) {}
}
