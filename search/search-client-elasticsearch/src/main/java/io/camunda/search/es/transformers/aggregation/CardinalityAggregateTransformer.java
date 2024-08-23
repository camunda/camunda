/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import co.elastic.clients.elasticsearch._types.aggregations.CardinalityAggregate;
import io.camunda.search.clients.aggregation.SearchAggregateBuilders;
import io.camunda.search.clients.aggregation.SearchCardinalityAggregate;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public class CardinalityAggregateTransformer
    extends AggregateOptionTransformer<CardinalityAggregate, SearchCardinalityAggregate> {

  public CardinalityAggregateTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchCardinalityAggregate apply(final CardinalityAggregate value) {
    return SearchAggregateBuilders.cardinality().value(value.value()).build();
  }
}
