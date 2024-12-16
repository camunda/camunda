/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class SearchFilterAggregationTransformer
    extends AggregationTransformer<SearchFilterAggregator, Aggregation> {

  public SearchFilterAggregationTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchFilterAggregator value) {
    final Query query = getQueryTransformer().apply(value.query());
    return Aggregation.of(a -> a.filter(query));
  }
}
