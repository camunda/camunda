/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchBucketSortAggregator;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.BucketSortAggregation;

public final class SearchBucketSortAggregationTransformer
    extends AggregatorTransformer<SearchBucketSortAggregator, Aggregation> {

  public SearchBucketSortAggregationTransformer(final OpensearchTransformers transformers) {
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
    final List<SortOptions> result = new ArrayList<>();

    for (final FieldSorting fieldSort : requestedSort) {
      result.add(
          SortOptions.of(
              s -> s.field(f -> f.field(fieldSort.field()).order(toOrder(fieldSort.order())))));
    }
    return result;
  }

  private SortOrder toOrder(final io.camunda.search.sort.SortOrder order) {
    return order == io.camunda.search.sort.SortOrder.ASC ? SortOrder.Asc : SortOrder.Desc;
  }
}
