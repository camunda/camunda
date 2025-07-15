/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public class SearchTopHitsAggregatorTransformer
    extends AggregatorTransformer<SearchTopHitsAggregator, Aggregation> {

  public SearchTopHitsAggregatorTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchTopHitsAggregator value) {
    final var builder =
        new Aggregation.Builder()
            .topHits(
                topHitsAggBuilder -> {
                  topHitsAggBuilder
                      .size(value.size())
                      .sort(
                          sortBuilder -> {
                            sortBuilder.field(
                                fieldSort -> {
                                  fieldSort.field(value.field());
                                  fieldSort.order(SortOrder.Desc);
                                  return fieldSort;
                                });
                            return sortBuilder;
                          });
                  return topHitsAggBuilder;
                });
    applySubAggregations(builder, value);
    return builder.build();
  }
}
