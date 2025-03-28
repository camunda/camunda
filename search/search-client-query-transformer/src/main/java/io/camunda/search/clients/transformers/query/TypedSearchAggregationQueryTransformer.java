/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.sort.NoSort;
import java.util.List;

public class TypedSearchAggregationQueryTransformer<F extends FilterBase, A extends AggregationBase>
    implements ServiceTransformer<TypedSearchAggregationQuery<F, A>, SearchQueryRequest> {

  private final ServiceTransformers transformers;
  private final TypedSearchQueryTransformer<F, NoSort> typedSearchQueryTransformer;

  public TypedSearchAggregationQueryTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
    typedSearchQueryTransformer = new TypedSearchQueryTransformer<>(transformers);
  }

  @Override
  public SearchQueryRequest apply(final TypedSearchAggregationQuery<F, A> query) {
    return typedSearchQueryTransformer.apply(query).toBuilder()
        .aggregations(applyAggregations(query.aggregation()))
        .build();
  }

  protected List<SearchAggregator> applyAggregations(final A aggregation) {
    return transformers.getAggregationTransformer(aggregation.getClass()).apply(aggregation);
  }
}
