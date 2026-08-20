/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

/**
 * A generic base class for transforming aggregation options into Elasticsearch aggregations.
 *
 * @param <T> The type of the input aggregation option (e.g., SearchTermsAggregation)
 * @param <R> The type of the resulting Elasticsearch aggregation (e.g., TermsAggregation)
 */
public abstract class AggregatorTransformer<T extends SearchAggregator, R extends Aggregation>
    extends OpensearchTransformer<T, R> implements SearchTransfomer<T, R> {

  public AggregatorTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  protected void applySubAggregations(
      final Aggregation.Builder.ContainerBuilder b, final SearchAggregator aggregator) {
    final var aggregations = aggregator.getAggregations();
    if (aggregations != null && !aggregations.isEmpty()) {
      b.aggregations(
          aggregations.stream()
              .map(
                  aggregation ->
                      Map.entry(
                          aggregation.getName(),
                          transformers
                              .getSearchAggregationTransformer(aggregation.getClass())
                              .apply(aggregation)))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
  }
}
