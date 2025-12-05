/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.util.Optional;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.CardinalityAggregation;

public final class SearchCardinalityAggregationTransformer
    extends AggregatorTransformer<SearchCardinalityAggregator, Aggregation> {

  public SearchCardinalityAggregationTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchCardinalityAggregator value) {
    // Create the CardinalityAggregation
    final CardinalityAggregation.Builder cardinalityBuilder = AggregationBuilders.cardinality();

    Optional.ofNullable(value.script())
        .ifPresent(
            script ->
                cardinalityBuilder.script(
                    Script.of(
                        b -> {
                          b.inline(
                              f -> {
                                f.source(script);
                                Optional.ofNullable(value.lang()).ifPresent(f::lang);
                                return f;
                              });
                          return b;
                        })));
    Optional.ofNullable(value.field()).ifPresent(cardinalityBuilder::field);
    final var builder = new Aggregation.Builder().cardinality(cardinalityBuilder.build());
    applySubAggregations(builder, value);
    return builder.build();
  }
}
