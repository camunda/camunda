/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import io.camunda.search.clients.aggregation.SearchAggregation;
import io.camunda.search.clients.aggregation.SearchTermsAggregation;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.Map;
import java.util.stream.Collectors;

public class TermsAggregationTransformer
    extends AggregationOptionTransformer<SearchTermsAggregation, TermsAggregation> {

  public TermsAggregationTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public TermsAggregation apply(final SearchTermsAggregation value) {
    final var subAggregations = of(value.subAggregations());
    return new Aggregation.Builder()
        .terms(AggregationBuilders.terms().field(value.field()).size(value.size()).build())
        .aggregations(subAggregations)
        .build()
        .terms();
  }

  private Map<String, Aggregation> of(final Map<String, SearchAggregation> subAggregations) {
    final var aggregationTransformer = getAggregationTransformer();
    return subAggregations.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, entry -> aggregationTransformer.apply(entry.getValue())));
  }
}
