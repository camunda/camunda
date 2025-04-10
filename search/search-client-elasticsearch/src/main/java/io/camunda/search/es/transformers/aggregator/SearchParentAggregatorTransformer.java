/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import io.camunda.search.clients.aggregator.SearchParentAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public class SearchParentAggregatorTransformer
    extends AggregatorTransformer<SearchParentAggregator, Aggregation> {

  public SearchParentAggregatorTransformer(final ElasticsearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public Aggregation apply(final SearchParentAggregator value) {
    final var parentAggregation = AggregationBuilders.parent().type(value.type()).build();

    final var builder = new Aggregation.Builder().parent(parentAggregation);
    applySubAggregations(builder, value);
    return builder.build();
  }
}
