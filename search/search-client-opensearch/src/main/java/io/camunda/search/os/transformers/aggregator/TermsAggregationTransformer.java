/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;

public final class TermsAggregationTransformer
    extends AggregationOptionTransformer<SearchTermsAggregator, Aggregation> {

  public TermsAggregationTransformer(final OpensearchTransformers transformers) {
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

    // Wrap it inside an Aggregation object
    return Aggregation.of(a -> a.terms(termsAggregation));
  }
}
