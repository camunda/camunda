/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.search.clients.aggregator.SearchFiltersAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class SearchFiltersAggregatorTransformer
    extends AggregatorTransformer<SearchFiltersAggregator, Aggregation> {

  public SearchFiltersAggregatorTransformer(final ElasticsearchTransformers transformers) {
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
