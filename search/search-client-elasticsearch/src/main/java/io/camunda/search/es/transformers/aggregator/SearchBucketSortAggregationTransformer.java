/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.BucketSortAggregation;
import io.camunda.search.clients.aggregator.SearchBucketSortAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import java.util.List;
import java.util.Optional;

public final class SearchBucketSortAggregationTransformer
    extends AggregatorTransformer<SearchBucketSortAggregator, Aggregation> {

  public SearchBucketSortAggregationTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchBucketSortAggregator value) {
    // Create the BucketSortAggregation
    final BucketSortAggregation.Builder bucketSortBuilder = AggregationBuilders.bucketSort();
    Optional.ofNullable(value.sorting())
        .ifPresent(sorting -> bucketSortBuilder.sort(toSort(sorting)));
    Optional.ofNullable(value.from()).ifPresent(bucketSortBuilder::from);
    Optional.ofNullable(value.size()).ifPresent(bucketSortBuilder::size);
    final var builder = new Aggregation.Builder().bucketSort(bucketSortBuilder.build());
    applySubAggregations(builder, value);
    return builder.build();
  }

  private List<SortOptions> toSort(final List<FieldSorting> requestedSort) {
    return requestedSort.stream()
        .map(
            fieldSort ->
                SortOptions.of(
                    s ->
                        s.field(f -> f.field(fieldSort.field()).order(toOrder(fieldSort.order())))))
        .toList();
  }

  private SortOrder toOrder(final io.camunda.search.sort.SortOrder order) {
    return order == io.camunda.search.sort.SortOrder.ASC ? SortOrder.Asc : SortOrder.Desc;
  }
}
