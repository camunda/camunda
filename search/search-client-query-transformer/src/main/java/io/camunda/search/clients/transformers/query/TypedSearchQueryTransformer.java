/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.clients.core.RequestBuilders.searchRequest;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.filter.FilterTransformer;
import io.camunda.search.clients.transformers.result.ResultConfigTransformer;
import io.camunda.search.clients.transformers.sort.SortingTransformer;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.result.QueryResultConfig;
import io.camunda.search.sort.NoSort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOption;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class TypedSearchQueryTransformer<F extends FilterBase, S extends SortOption>
    implements ServiceTransformer<TypedSearchQuery<F, S>, SearchQueryRequest> {

  private final ServiceTransformers transformers;

  public TypedSearchQueryTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQueryRequest apply(final TypedSearchQuery<F, S> query) {
    final var filter = query.filter();
    final var searchQueryFilter = toSearchQuery(filter);
    final var indices = toIndices(filter);

    final var page = query.page();
    final var reverse = !page.isNextPage();

    final var builder =
        searchRequest().index(indices).query(searchQueryFilter).from(page.from()).size(page.size());

    final var sort = query.sort();
    if (!(sort instanceof NoSort)) {
      final var sorting = toSearchSortOptions(sort, reverse);
      if (!sorting.isEmpty()) {
        builder.sort(query.retainValidSortings(sorting));
      }
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
    if (resultConfig == null) {
      return null;
    }

    final var resultConfigTransformer = getResultConfigTransformer(resultConfig.getClass());
    return resultConfigTransformer.apply(resultConfig);
  }

  private SearchQuery toSearchQuery(final F filter) {
    return getFilterTransformer(filter).apply(filter);
  }

  private List<String> toIndices(final F filter) {
    return List.of(getFilterTransformer(filter).getIndex().getAlias());
  }

  private List<SearchSortOptions> toSearchSortOptions(final S sort, final boolean reverse) {
    final var orderings = sort.getFieldSortings();
    final var sortingTransformer = getSortingTransformer(sort.getClass());
    return sortingTransformer.apply(Tuple.of(orderings, reverse));
  }

  private FilterTransformer<F> getFilterTransformer(final F filter) {
    return transformers.getFilterTransformer(filter.getClass());
  }

  private SortingTransformer getSortingTransformer(final Class<? extends SortOption> cls) {
    return new SortingTransformer(transformers.getFieldSortingTransformer(cls));
  }

  private <T extends QueryResultConfig>
      ResultConfigTransformer<QueryResultConfig> getResultConfigTransformer(final Class<T> clazz) {
    final ServiceTransformer<QueryResultConfig, SearchSourceConfig> transformer =
        transformers.getTransformer(clazz);
    return (ResultConfigTransformer) transformer;
  }
}
