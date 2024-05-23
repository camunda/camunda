/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.types;

import io.camunda.data.clients.util.DataStoreSortOptionsBuildersDelegate;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class DataStoreSortOptionsBuilders {

  private static DataStoreSortOptionsBuildersDelegate sortOptionsBuilders;

  private DataStoreSortOptionsBuilders() {}

  public static void setSortOptionsBuilders(
      final DataStoreSortOptionsBuildersDelegate sortOptionsBuilders) {
    DataStoreSortOptionsBuilders.sortOptionsBuilders = sortOptionsBuilders;
  }

  public static SortOrder toSortOrder(final String sortOrder) {
    return SortOrder.valueOf(sortOrder);
  }

  public static SortOrder reverseOrder(final SortOrder sortOrder) {
    return sortOrder == SortOrder.ASC ? SortOrder.DESC : SortOrder.ASC;
  }

  public static DataStoreSortOptions sortOptions(String field, SortOrder sortOrder) {
    return DataStoreSortOptions.of(so -> so.field(sf -> sf.field(field).order(sortOrder)));
  }

  public static DataStoreSortOptions sortOptions(
      String field, SortOrder sortOrder, String missing) {
    return DataStoreSortOptions.of(
        so -> so.field(sf -> sf.field(field).order(sortOrder).missing(missing)));
  }

  public static DataStoreSortOptions.Builder sort() {
    return sortOptionsBuilders.sort();
  }

  public static DataStoreSortOptions sort(
      final Function<DataStoreSortOptions.Builder, DataStoreObjectBuilder<DataStoreSortOptions>>
          fn) {
    return sortOptionsBuilders.sort(fn);
  }

  public static DataStoreFieldSort.Builder field() {
    return sortOptionsBuilders.field();
  }

  public static DataStoreFieldSort field(
      final Function<DataStoreFieldSort.Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn) {
    return sortOptionsBuilders.field(fn);
  }
}
