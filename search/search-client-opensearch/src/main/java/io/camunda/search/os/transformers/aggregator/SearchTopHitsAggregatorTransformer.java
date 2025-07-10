/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public class SearchTopHitsAggregatorTransformer
    extends AggregatorTransformer<SearchTopHitsAggregator, Aggregation> {

  public SearchTopHitsAggregatorTransformer(final OpensearchTransformers transformers) {
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
