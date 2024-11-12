/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class SortOptionsBuilders {

  private SortOptionsBuilders() {}

  public static SortOrder toSortOrder(final String sortOrder) {
    return SortOrder.valueOf(sortOrder);
  }

  public static SortOrder reverseOrder(final SortOrder sortOrder) {
    return sortOrder == SortOrder.ASC ? SortOrder.DESC : SortOrder.ASC;
  }

  public static SearchSortOptions sortOptions(final String field, final SortOrder sortOrder) {
    return SearchSortOptions.of(so -> so.field(sf -> sf.field(field).order(sortOrder)));
  }

  public static SearchSortOptions sortOptions(
      final String field, final SortOrder sortOrder, final String missing) {
    return SearchSortOptions.of(
        so -> so.field(sf -> sf.field(field).order(sortOrder).missing(missing)));
  }

  public static SearchSortOptions.Builder sort() {
    return new SearchSortOptions.Builder();
  }

  public static SearchSortOptions sort(
      final Function<SearchSortOptions.Builder, ObjectBuilder<SearchSortOptions>> fn) {
    return fn.apply(sort()).build();
  }

  public static SearchFieldSort.Builder field() {
    return new SearchFieldSort.Builder();
  }

  public static SearchFieldSort field(
      final Function<SearchFieldSort.Builder, ObjectBuilder<SearchFieldSort>> fn) {
    return fn.apply(field()).build();
  }
}
