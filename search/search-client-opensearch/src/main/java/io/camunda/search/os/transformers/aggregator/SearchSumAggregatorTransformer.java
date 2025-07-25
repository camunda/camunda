/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchSumAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;

public class SearchSumAggregatorTransformer
    extends AggregatorTransformer<SearchSumAggregator, Aggregation> {

  public SearchSumAggregatorTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public Aggregation apply(final SearchSumAggregator value) {
    final var sumAggregation = AggregationBuilders.sum().field(value.field()).build();

    final var builder = new Aggregation.Builder().sum(sumAggregation);
    applySubAggregations(builder, value);
    return builder.build();
  }
}
