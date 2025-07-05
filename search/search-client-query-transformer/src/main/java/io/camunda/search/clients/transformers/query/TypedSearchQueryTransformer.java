/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.clients.core.RequestBuilders.searchRequest;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.control.ResourceAccessControl;
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
import java.util.Optional;

public class TypedSearchQueryTransformer<F extends FilterBase, S extends SortOption>
    implements ServiceTransformer<TypedSearchQuery<F, S>, SearchQueryRequest> {

  private final ServiceTransformers transformers;
  private final ResourceAccessControl resourceAccessControl;

  public TypedSearchQueryTransformer(final ServiceTransformers transformers) {
    this(transformers, null);
  }

  public TypedSearchQueryTransformer(
      final ServiceTransformers transformers, final ResourceAccessControl resourceAccessControl) {
    this.transformers = transformers;
    this.resourceAccessControl = resourceAccessControl;
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
    if (searchAfter != null) {
      builder.searchAfter(Cursor.decode(searchAfter));
    }

    final var resultConfig = query.resultConfig();
    final var searchQuerySourceConfig = toSearchSourceConfig(resultConfig);
    if (searchQuerySourceConfig != null) {
      builder.source(searchQuerySourceConfig);
    }

    Optional.ofNullable(query.aggregation())
        .ifPresent(aggregation -> builder.aggregations(toAggregations(aggregation)));

    return builder.build();
  }

  public TypedSearchQueryTransformer<F, S> withResourceAccessControl(
      final ResourceAccessControl resourceAccessControl) {
    return new TypedSearchQueryTransformer<>(transformers, resourceAccessControl);
  }

  private SearchSourceConfig toSearchSourceConfig(final QueryResultConfig resultConfig) {
    if (resultConfig == null) {
      return null;
    }

    final var resultConfigTransformer = getResultConfigTransformer(resultConfig.getClass());
    return resultConfigTransformer.apply(resultConfig);
  }

  private SearchQuery toSearchQuery(final F filter) {
    return getFilterTransformer(filter)
        .withResourceAccessControl(resourceAccessControl)
        .apply(filter);
  }

  private List<String> toIndices(final F filter) {
    return List.of(getFilterTransformer(filter).getIndex().getAlias());
  }

  protected List<SearchAggregator> toAggregations(final AggregationBase aggregation) {
    return transformers.getAggregationTransformer(aggregation.getClass()).apply(aggregation);
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
