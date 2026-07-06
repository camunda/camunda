/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchMinAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public class SearchMinAggregatorTransformer
    extends AggregatorTransformer<SearchMinAggregator, Aggregation> {

  public SearchMinAggregatorTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public Aggregation apply(final SearchMinAggregator value) {
    final var builder = new Aggregation.Builder().min(m -> m.field(value.field()));
    applySubAggregations(builder, value);
    return builder.build();
  }
}
