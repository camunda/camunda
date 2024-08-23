/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.CardinalityAggregation;
import io.camunda.search.clients.aggregation.SearchCardinalityAggregation;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class CardinalityAggregationTransformer
    extends AggregationOptionTransformer<SearchCardinalityAggregation, CardinalityAggregation> {

  public CardinalityAggregationTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public CardinalityAggregation apply(final SearchCardinalityAggregation value) {
    return AggregationBuilders.cardinality()
        .field(value.field())
        .precisionThreshold(value.precisionThreshold())
        .build();
  }
}
