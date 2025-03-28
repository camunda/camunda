/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.NoSort;
import java.util.List;

public abstract class TypedSearchAggregationQueryTransformer<F extends FilterBase>
    extends TypedSearchQueryTransformer<F, NoSort> {

  public TypedSearchAggregationQueryTransformer(final ServiceTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchQueryRequest apply(final TypedSearchQuery<F, NoSort> query) {
    return super.apply(query).toBuilder().aggregations(applyAggregations()).build();
  }

  protected abstract List<SearchAggregator> applyAggregations();
}
