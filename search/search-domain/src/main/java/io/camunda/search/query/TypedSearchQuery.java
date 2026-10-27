/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.result.QueryResultConfig;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOption;
import java.util.List;

public interface TypedSearchQuery<F extends FilterBase, S extends SortOption>
    extends SearchQueryBase {

  F filter();

  S sort();

  default AggregationBase aggregation() {
    return null;
  }

  default QueryResultConfig resultConfig() {
    return null;
  }

  default List<SearchSortOptions> retainValidSortings(final List<SearchSortOptions> sorting) {
    return sorting;
  }
}
