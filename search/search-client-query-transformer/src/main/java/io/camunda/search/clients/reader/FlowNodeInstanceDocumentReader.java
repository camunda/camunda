/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class FlowNodeInstanceDocumentReader extends DocumentBasedReader
    implements FlowNodeInstanceReader {

  public FlowNodeInstanceDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public FlowNodeInstanceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            FlowNodeInstanceQuery.of(
                b -> b.filter(f -> f.flowNodeInstanceKeys(key)).singleResult()),
            io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> search(
      final FlowNodeInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {

    final var filter = query.filter();
    if (filter.scopeKeys() != null && !filter.scopeKeys().isEmpty()) {
      return searchWithScopeKeyFallback(query, resourceAccessChecks);
    }

    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity.class,
            resourceAccessChecks);
  }

  /**
   * Executes a two-step fallback search when the filter includes scopeKeys.
   *
   * <p>This approach is necessary because the {@code scopeKey} field is not guaranteed to be
   * populated for all data, particularly in datasets created before version 8.8. Therefore, we
   * first attempt to resolve the scopeKey to a flow node instance. If none is found, we assume it's
   * a process instance key and retrieve its immediate children using level-based filtering. If an
   * element instance is found, we then search for its children using the treePath and level values.
   *
   * <p>Once {@code scopeKey} is universally populated in the index, this fallback logic can be
   * removed.
   */
  private SearchQueryResult<FlowNodeInstanceEntity> searchWithScopeKeyFallback(
      final FlowNodeInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {

    final var filter = query.filter();
    final var firstResult =
        search(
            FlowNodeInstanceQuery.of(
                b -> b.filter(f -> f.flowNodeInstanceKeys(filter.scopeKeys()))),
            resourceAccessChecks);

    if (firstResult == null || firstResult.items().isEmpty()) {
      final var processInstanceQuery =
          FlowNodeInstanceQuery.of(
              b ->
                  b.filter(
                          f -> f.copyFrom(filter).processInstanceKeys(filter.scopeKeys()).levels(1))
                      .sort(query.sort())
                      .page(query.page()));

      return getSearchExecutor()
          .search(
              processInstanceQuery,
              io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity.class,
              resourceAccessChecks);

    } else {
      final var childLevel = firstResult.items().getFirst().level() + 1;
      final var treePath = firstResult.items().getFirst().treePath();
      final var elementInstanceQuery =
          FlowNodeInstanceQuery.of(
              b ->
                  b.filter(
                          f ->
                              f.copyFrom(filter)
                                  .treePaths(treePath)
                                  .levels(childLevel)
                                  .useTreePathPrefix(true))
                      .sort(query.sort())
                      .page(query.page()));

      return getSearchExecutor()
          .search(
              elementInstanceQuery,
              io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity.class,
              resourceAccessChecks);
    }
  }
}
