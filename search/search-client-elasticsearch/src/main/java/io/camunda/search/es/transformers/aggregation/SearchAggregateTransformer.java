/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.AggregateVariant;
import io.camunda.search.clients.aggregation.SearchAggregate;
import io.camunda.search.clients.aggregation.SearchAggregateOption;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.transformers.SearchTransfomer;

public class SearchAggregateTransformer
    extends ElasticsearchTransformer<Aggregate, SearchAggregate> {

  public SearchAggregateTransformer(final ElasticsearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public SearchAggregate apply(final Aggregate aggregate) {
    final var aggregateOption = (AggregateVariant) aggregate._get();

    if (aggregateOption == null) {
      return null;
    }

    final var aggregateVariantCls = aggregateOption.getClass();
    final var transformer = getAggregateOptionTransformer(aggregateVariantCls);
    final var transformedAggregationOption = transformer.apply(aggregateOption);
    return transformedAggregationOption.toSearchAggregate();
  }

  public <T extends AggregateVariant, R extends SearchAggregateOption>
      SearchTransfomer<T, R> getAggregateOptionTransformer(final Class<?> cls) {
    return getTransformer(cls);
  }
}
