/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.sort;

import static io.camunda.data.clients.sort.DataStoreSortOptionsBuilders.reverseOrder;
import static io.camunda.data.clients.sort.DataStoreSortOptionsBuilders.sortOptions;

import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.clients.sort.SortOrder;
import java.util.List;

public interface SortOption {

  List<DataStoreSortOptions> toSortOptions(final boolean reverse);

  public final record FieldSorting(String field, SortOrder order) {

    DataStoreSortOptions toSortOption(final boolean reverse) {
      if (!reverse) {
        return sortOptions(field, order, "_last");
      } else {
        return sortOptions(field, reverseOrder(order), "_first");
      }
    }
  }
}
