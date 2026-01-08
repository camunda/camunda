/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.cache;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads process definitions from search storage for the cache.
 *
 * <p>This provider executes queries with {@link ResourceAccessChecks#disabled()} since enrichment
 * is expected to have already applied access checks at the query level.
 */
public final class ProcessDefinitionProvider {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionProvider.class);

  private final SearchClientBasedQueryExecutor searchExecutor;

  public ProcessDefinitionProvider(final SearchClientBasedQueryExecutor searchExecutor) {
    this.searchExecutor = searchExecutor;
  }

  public ProcessCacheItem extractProcessData(final Long processDefinitionKey) {
    if (processDefinitionKey == null) {
      return ProcessCacheItem.EMPTY;
    }

    final Map<Long, ProcessCacheItem> result = extractProcessData(Set.of(processDefinitionKey));
    return result.getOrDefault(processDefinitionKey, ProcessCacheItem.EMPTY);
  }

  public Map<Long, ProcessCacheItem> extractProcessData(final Set<Long> processDefinitionKeys) {
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
          searchExecutor.search(query, ProcessEntity.class, ResourceAccessChecks.disabled());

      final Map<Long, ProcessCacheItem> fetched = new HashMap<>();
      result.items().stream()
          .filter(Objects::nonNull)
          .forEach(
              pd ->
                  fetched.put(
                      pd.processDefinitionKey(),
                      new ProcessCacheItem(
                          pd.processDefinitionId(), pd.name(), pd.version(), pd.tenantId())));
      return fetched;
    } catch (final Exception ex) {
      LOG.warn(
          "Failed to fetch process definitions for keys {}. Returning EMPTY for all keys.",
          processDefinitionKeys,
          ex);

      final Map<Long, ProcessCacheItem> fallback = new HashMap<>();
      processDefinitionKeys.forEach(key -> fallback.put(key, ProcessCacheItem.EMPTY));
      return fallback;
    }
  }
}
