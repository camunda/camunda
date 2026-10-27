/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.search.sort.SortOptionsBuilders.reverseOrder;
import static io.camunda.search.sort.SortOptionsBuilders.sortOptions;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class SortingTransformer
    implements ServiceTransformer<Tuple<List<FieldSorting>, Boolean>, List<SearchSortOptions>> {

  private final FieldSortingTransformer fieldSortingTransformer;

  public SortingTransformer(final FieldSortingTransformer fieldSortingTransformer) {
    this.fieldSortingTransformer = fieldSortingTransformer;
  }

  @Override
  public List<SearchSortOptions> apply(final Tuple<List<FieldSorting>, Boolean> value) {
    final var orderings = value.getLeft();
    final var reverse = value.getRight();
    final var sorting = map(orderings, reverse);
    final var defaultSorting = getDefaultSearchSortOption(reverse);
    sorting.add(defaultSorting);
    return sorting;
  }

  private List<SearchSortOptions> map(final List<FieldSorting> orderings, final boolean reverse) {
    final List<SearchSortOptions> sorting;
    if (orderings != null && !orderings.isEmpty()) {
      sorting =
          orderings.stream()
              .flatMap((f) -> toSearchSortOptions(f, reverse).stream())
              .collect(Collectors.toList());
    } else {
      sorting = new ArrayList<>();
    }
    return sorting;
  }

  private List<SearchSortOptions> toSearchSortOptions(
      final FieldSorting value, final boolean reverse) {
    final var order = value.order();
    return fieldSortingTransformer.applyAll(value.field()).stream()
        .map(field -> toSearchSortOption(field, order, reverse))
        .toList();
  }

  private SearchSortOptions toSearchSortOption(
      final String field, final SortOrder order, final boolean reverse) {
    return !reverse
        ? sortOptions(field, order, "_last")
        : sortOptions(field, reverseOrder(order), "_first");
  }

  private SearchSortOptions getDefaultSearchSortOption(final boolean reverse) {
    return sortOptions(
        fieldSortingTransformer.defaultSortField(), reverse ? SortOrder.DESC : SortOrder.ASC);
  }
}
