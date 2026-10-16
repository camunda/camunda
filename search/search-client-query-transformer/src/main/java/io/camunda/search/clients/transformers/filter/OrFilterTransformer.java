/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.OrFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mixed into a {@link FilterTransformer} for a filter that supports a top-level {@code $or} clause,
 * to expand {@link OrFilter#orFilters()} into the shared {@code (top-level fields) AND (any of the
 * $or groups)} query shape.
 */
public interface OrFilterTransformer<T extends OrFilter<T>> {

  List<SearchQuery> toSearchQueryFields(T filter);

  /** The {@code $or} clause as a single bool query, or empty if it would be a no-op. */
  default Optional<SearchQuery> toOrClause(final T filter) {
    if (filter.orFilters() == null || filter.orFilters().isEmpty() || filter.hasEmptyOrFilter()) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        or(filter.orFilters().stream().map(f -> and(toSearchQueryFields(f))).toList()));
  }

  /** {@link #toSearchQueryFields(Object)} with the {@link #toOrClause(Object)} appended. */
  default List<SearchQuery> toSearchQueryFieldsWithOr(final T filter) {
    final var queries = new ArrayList<>(toSearchQueryFields(filter));
    toOrClause(filter).ifPresent(queries::add);
    return queries;
  }
}
