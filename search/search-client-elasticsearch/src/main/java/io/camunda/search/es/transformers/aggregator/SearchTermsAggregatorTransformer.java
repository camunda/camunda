/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.util.NamedValue;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import java.util.List;
import java.util.Optional;

public final class SearchTermsAggregatorTransformer
    extends AggregatorTransformer<SearchTermsAggregator, Aggregation> {

  public SearchTermsAggregatorTransformer(final ElasticsearchTransformers transformers) {
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

    final var builder = new Aggregation.Builder().terms(termsAggregation.build());
    applySubAggregations(builder, value);
    return builder.build();
  }

  private List<NamedValue<SortOrder>> toOrder(final List<FieldSorting> sorting) {
    return sorting.stream()
        .map(
            fieldSorting ->
                new NamedValue<>(
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
