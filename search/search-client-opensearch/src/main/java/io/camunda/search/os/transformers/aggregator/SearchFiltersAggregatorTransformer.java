/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchFiltersAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.FiltersAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class SearchFiltersAggregatorTransformer
    extends AggregatorTransformer<SearchFiltersAggregator, Aggregation> {

  public SearchFiltersAggregatorTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchFiltersAggregator value) {
    final var queries =
        value.queries().entrySet().stream()
            .collect(
                Collectors.toMap(Entry::getKey, e -> getQueryTransformer().apply(e.getValue())));
    final var filterBuckets =
        new FiltersAggregation.Builder()
            .filters(new Buckets.Builder<Query>().keyed(queries).build())
            .build();

    final var builder = new Aggregation.Builder().filters(filterBuckets);
    applySubAggregations(builder, value);
    return builder.build();
  }
}
