/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.sort;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class DataStoreSortOptionsBuilders {

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
    return new DataStoreSortOptions.Builder();
  }

  public static DataStoreSortOptions sort(
      final Function<DataStoreSortOptions.Builder, DataStoreObjectBuilder<DataStoreSortOptions>>
          fn) {
    return fn.apply(sort()).build();
  }

  public static DataStoreFieldSort.Builder field() {
    return new DataStoreFieldSort.Builder();
  }

  public static DataStoreFieldSort field(
      final Function<DataStoreFieldSort.Builder, DataStoreObjectBuilder<DataStoreFieldSort>> fn) {
    return fn.apply(field()).build();
  }
}
