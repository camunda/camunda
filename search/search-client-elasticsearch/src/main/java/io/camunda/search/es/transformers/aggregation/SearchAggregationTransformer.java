/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationVariant;
import io.camunda.search.clients.aggregation.SearchAggregation;
import io.camunda.search.clients.aggregation.SearchAggregationOption;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.transformers.SearchTransfomer;

public class SearchAggregationTransformer
    extends ElasticsearchTransformer<SearchAggregation, Aggregation> {

  public SearchAggregationTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchAggregation searchAggregation) {
    final var aggregationOption = searchAggregation.aggregationOption();

    if (aggregationOption == null) {
      return null;
    }

    final var aggregationOptionCls = aggregationOption.getClass();
    final var transformer = getAggregationOptionTransformer(aggregationOptionCls);
    final var transformedAggregationOption = transformer.apply(aggregationOption);
    return transformedAggregationOption._toAggregation();
  }

  public <T extends SearchAggregationOption, R extends AggregationVariant>
      SearchTransfomer<T, R> getAggregationOptionTransformer(final Class<?> cls) {
    return getTransformer(cls);
  }
}
