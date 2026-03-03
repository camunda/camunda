/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.CursorForwardPaginatedAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.SortOption;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public abstract class DocumentBasedReader {

  protected final SearchClientBasedQueryExecutor executor;
  protected final IndexDescriptor indexDescriptor;

  public DocumentBasedReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    this.executor = executor;
    this.indexDescriptor = indexDescriptor;
  }

  protected SearchClientBasedQueryExecutor getSearchExecutor() {
    return executor;
  }

  protected <
          F extends FilterBase,
          S extends SortOption,
          E,
          R extends CursorForwardPaginatedAggregationResult<E>>
      SearchQueryResult<E> aggregateToResult(
          final TypedSearchQuery<F, S> query,
          final Class<R> resultClass,
          final ResourceAccessChecks resourceAccessChecks) {
    final CursorForwardPaginatedAggregationResult<E> aggResult =
        getSearchExecutor().aggregate(query, resultClass, resourceAccessChecks);
    return new SearchQueryResult<>(
        aggResult.items().size(),
        !aggResult.items().isEmpty(),
        aggResult.items(),
        null,
        aggResult.endCursor());
  }
}
