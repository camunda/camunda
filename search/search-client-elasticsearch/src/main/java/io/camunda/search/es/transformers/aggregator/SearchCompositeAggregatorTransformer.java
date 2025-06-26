/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchCompositeAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.query.Cursor;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchCompositeAggregatorTransformer
    extends AggregatorTransformer<SearchCompositeAggregator, Aggregation> {

  private final AggregationCursorTransformer cursorTransformer = new AggregationCursorTransformer();

  public SearchCompositeAggregatorTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchCompositeAggregator value) {
    final var compositeAggBuilder =
        new CompositeAggregation.Builder()
            .size(value.size())
            .sources(toCompositeAggregationSource(value.sources()));
    final var after = Cursor.decode(value.after());
    if (after != null) {
      compositeAggBuilder.after(cursorTransformer.apply(after));
    }

    final var builder = new Aggregation.Builder().composite(compositeAggBuilder.build());
    applySubAggregations(builder, value);
    return builder.build();
  }

  private List<Map<String, CompositeAggregationSource>> toCompositeAggregationSource(
      final List<SearchAggregator> aggregators) {
    return aggregators.stream()
        .map(
            agg ->
                Map.of(
                    agg.getName(),
                    CompositeAggregationSource.of(
                        sourceBuilder ->
                            switch (agg) {
                              case final SearchTermsAggregator terms ->
                                  sourceBuilder.terms(
                                      termsBuilder -> termsBuilder.field(terms.field()));
                              default ->
                                  throw new IllegalStateException(
                                      "Unsupported aggregator type: " + agg.getClass());
                            })))
        .collect(Collectors.toList());
  }
}
