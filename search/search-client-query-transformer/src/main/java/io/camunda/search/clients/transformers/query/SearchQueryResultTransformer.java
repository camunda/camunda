/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.TypedSearchQuery;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class SearchQueryResultTransformer<T, R> {
  final ServiceTransformer<T, R> documentToEntityMapper;

  public SearchQueryResultTransformer(final ServiceTransformer<T, R> documentToEntityMapper) {
    this.documentToEntityMapper = documentToEntityMapper;
  }

  /**
   * Applies the transformation to the search query response.
   *
   * @param value The value to be transformed.
   * @param reverse Indicates whether to reverse the order of the hits. This is required when the
   *     query requests a previous page (see {@link
   *     TypedSearchQueryTransformer#apply(TypedSearchQuery)}). In such cases, the search query is
   *     executed with reverse sorting, and the response hits must be reversed again to restore the
   *     correct order.
   * @return The transformed search query result.
   */
  public SearchQueryResult<R> apply(final SearchQueryResponse<T> value, final boolean reverse) {
    final var hits = reverse ? value.hits().reversed() : value.hits();
    final var items = of(hits);
    final var size = hits.size();
    final Object[] firstSortValues;
    final Object[] lastSortValues;
    if (size > 0) {
      firstSortValues = hits.getFirst().sortValues();
      lastSortValues = hits.getLast().sortValues();
    } else {
      firstSortValues = null;
      lastSortValues = null;
    }

    return new Builder<R>()
        .total(value.totalHits())
        .firstSortValues(firstSortValues)
        .lastSortValues(lastSortValues)
        .items(items.stream().map(documentToEntityMapper::apply).toList())
        .build();
  }

  private List<T> of(final List<SearchQueryHit<T>> values) {
    if (values != null) {
      return values.stream().map(SearchQueryHit::source).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }
}
