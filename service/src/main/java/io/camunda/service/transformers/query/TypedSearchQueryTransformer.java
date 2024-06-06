/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.query;

import static io.camunda.search.clients.core.RequestBuilders.searchRequest;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.sort.SearchSortOptions;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.query.TypedSearchQuery;
import io.camunda.service.search.sort.SortOption;
import io.camunda.service.search.sort.SortOption.FieldSorting;
import io.camunda.service.transformers.ServiceTransformer;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.service.transformers.filter.FilterTransformer;
import io.camunda.service.transformers.sort.FieldSortingTransformer;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public final class TypedSearchQueryTransformer<F extends FilterBase, S extends SortOption>
    implements SearchRequestTransformer<F, S> {

  private final ServiceTransformers transformers;

  public TypedSearchQueryTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQueryRequest toSearchQueryRequest(
      final TypedSearchQuery<F, S> query, final SearchQuery authCheck) {
    final var filter = query.filter();
    final var searchQueryFilter = toSearchQuery(filter, authCheck);
    final var indices = toIndices(filter);

    final var page = query.page();
    final var reverse = !page.isNextPage();

    final var builder =
        searchRequest().index(indices).query(searchQueryFilter).from(page.from()).size(page.size());

    final var sort = query.sort();
    final var sorting = toSearchSortOptions(sort, reverse);
    if (sorting != null && !sorting.isEmpty()) {
      builder.sort(sorting);
    }

    final var searchAfter = page.startNextPageAfter();
    if (searchAfter != null && searchAfter.length > 0) {
      builder.searchAfter(searchAfter);
    }

    return builder.build();
  }

  protected SearchQuery toSearchQuery(final F filter, final SearchQuery authCheck) {
    final var filterTransformer = getFilterTransformer(filter.getClass());
    final var transformedQuery = filterTransformer.apply(filter);
    return and(transformedQuery, authCheck);
  }

  protected List<String> toIndices(final F filter) {
    final var filterTransformer = getFilterTransformer(filter.getClass());
    return filterTransformer.toIndices(filter);
  }

  protected List<SearchSortOptions> toSearchSortOptions(final S sort, final boolean reverse) {
    final var orderings = sort.getFieldSortings();
    final var sortingTransformer = getFieldSortingTransformer();
    return sortingTransformer.apply(Tuple.of(orderings, reverse));
  }

  protected FilterTransformer<F> getFilterTransformer(final Class<?> cls) {
    return transformers.getFilterTransformer(cls);
  }

  protected FieldSortingTransformer getFieldSortingTransformer() {
    final ServiceTransformer<Tuple<List<FieldSorting>, Boolean>, List<SearchSortOptions>>
        transformer = transformers.getTransformer(FieldSortingTransformer.class);
    return (FieldSortingTransformer) transformer;
  }
}
