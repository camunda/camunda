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
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class SearchTermsAggregatorTransformer
    extends AggregatorTransformer<SearchTermsAggregator, Aggregation> {

  public SearchTermsAggregatorTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchTermsAggregator value) {
    // Create the TermsAggregation
    final TermsAggregation termsAggregation =
        AggregationBuilders.terms()
            .field(value.field())
            .size(value.size())
            .minDocCount(value.minDocCount())
            .build();

    final var builder = new Aggregation.Builder().terms(termsAggregation);
    applySubAggregations(builder, value);
    return builder.build();
  }
}
