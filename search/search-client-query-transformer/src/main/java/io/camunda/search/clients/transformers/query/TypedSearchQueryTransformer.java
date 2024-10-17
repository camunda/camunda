/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.clients.core.RequestBuilders.searchRequest;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.filter.FilterTransformer;
import io.camunda.search.clients.transformers.result.ResultConfigTransformer;
import io.camunda.search.clients.transformers.sort.FieldSortingTransformer;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.result.QueryResultConfig;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.SortOption.FieldSorting;
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

    final var resultConfig = query.resultConfig();
    final var searchQuerySourceConfig = toSearchSourceConfig(resultConfig);
    if (searchQuerySourceConfig != null) {
      builder.source(searchQuerySourceConfig);
    }

    return builder.build();
  }

  private SearchSourceConfig toSearchSourceConfig(final QueryResultConfig resultConfig) {
    final var resultConfigTransformer = getResultConfigTransformer();
    return resultConfigTransformer.apply(resultConfig);
  }

  private SearchQuery toSearchQuery(final F filter, final SearchQuery authCheck) {
    final var filterTransformer = getFilterTransformer(filter.getClass());
    final var transformedQuery = filterTransformer.apply(filter);
    return and(transformedQuery, authCheck);
  }

  private List<String> toIndices(final F filter) {
    final var filterTransformer = getFilterTransformer(filter.getClass());
    return filterTransformer.toIndices(filter);
  }

  private List<SearchSortOptions> toSearchSortOptions(final S sort, final boolean reverse) {
    final var orderings = sort.getFieldSortings();
    final var sortingTransformer = getFieldSortingTransformer();
    return sortingTransformer.apply(Tuple.of(orderings, reverse));
  }

  private FilterTransformer<F> getFilterTransformer(final Class<?> cls) {
    return transformers.getFilterTransformer(cls);
  }

  private FieldSortingTransformer getFieldSortingTransformer() {
    final ServiceTransformer<Tuple<List<FieldSorting>, Boolean>, List<SearchSortOptions>>
        transformer = transformers.getTransformer(FieldSortingTransformer.class);
    return (FieldSortingTransformer) transformer;
  }

  private ResultConfigTransformer getResultConfigTransformer() {
    final ServiceTransformer<QueryResultConfig, SearchSourceConfig> transformer =
        transformers.getTransformer(QueryResultConfig.class);
    return (ResultConfigTransformer) transformer;
  }
}
