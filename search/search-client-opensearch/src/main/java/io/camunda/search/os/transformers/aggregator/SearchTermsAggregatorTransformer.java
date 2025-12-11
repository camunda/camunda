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
import io.camunda.search.sort.SortOption.FieldSorting;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;

public final class SearchTermsAggregatorTransformer
    extends AggregatorTransformer<SearchTermsAggregator, Aggregation> {

  public SearchTermsAggregatorTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchTermsAggregator value) {
    // Create the TermsAggregation
    final TermsAggregation.Builder termsAggregation =
        AggregationBuilders.terms()
            .field(value.field())
            .size(value.size())
            .minDocCount(value.minDocCount());
    Optional.ofNullable(value.sorting())
        .ifPresent(sorting -> termsAggregation.order(toOrder(sorting)));

    Optional.ofNullable(value.script())
        .ifPresent(
            script ->
                termsAggregation.script(
                    builder ->
                        builder.inline(
                            in -> {
                              final var inline = in.source(script);
                              return Optional.ofNullable(value.lang())
                                  .map(inline::lang)
                                  .orElse(inline);
                            })));

    final var builder = new Aggregation.Builder().terms(termsAggregation.build());
    applySubAggregations(builder, value);
    return builder.build();
  }

  private List<Map<String, SortOrder>> toOrder(final List<FieldSorting> sorting) {
    return sorting.stream()
        .map(
            fieldSorting ->
                Map.of(
                    fieldSorting.field(),
                    fieldSorting.order() == null
                        ? SortOrder.Asc
                        : toSortOrder(fieldSorting.order())))
        .toList();
  }

  private SortOrder toSortOrder(final io.camunda.search.sort.SortOrder order) {
    return order == io.camunda.search.sort.SortOrder.ASC ? SortOrder.Asc : SortOrder.Desc;
  }
}
