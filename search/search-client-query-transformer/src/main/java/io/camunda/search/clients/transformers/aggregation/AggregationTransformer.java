/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.function.Function;

public interface AggregationTransformer<A extends AggregationBase>
    extends ServiceTransformer<Tuple<A, ServiceTransformers>, List<SearchAggregator>> {

  /**
   * Finds the sort options for a given aggregation fields, transforming the fields names using the
   * provided transformers. Note: This is usually done in {@link
   * io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer}, but {@link
   * io.camunda.search.query.AggregationPaginated} are treated differently and must handle this on
   * their own.
   *
   * @param aggregationField the aggregation fields to find sort options for
   * @param transformers the service transformers to use for fields name transformation
   * @return a function that takes a {@link SortOption} and returns a list of {@link FieldSorting}
   *     matching the aggregation fields
   */
  default Function<SortOption, List<FieldSorting>> findSortOptionFor(
      final String aggregationField, final ServiceTransformers transformers) {
    return sortOption ->
        sortOption.getFieldSortings().stream()
            .map(
                ordering ->
                    new FieldSorting(
                        transformers
                            .getFieldSortingTransformer(sortOption.getClass())
                            .apply(ordering.field()),
                        ordering.order()))
            .filter(fs -> fs.field().equals(aggregationField))
            .toList();
  }
}
