/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchMaxAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public class SearchMaxAggregatorTransformer
    extends AggregatorTransformer<SearchMaxAggregator, Aggregation> {

  public SearchMaxAggregatorTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public Aggregation apply(final SearchMaxAggregator value) {
    final var builder = new Aggregation.Builder().max(m -> m.field(value.field()));
    applySubAggregations(builder, value);
    return builder.build();
  }
}
