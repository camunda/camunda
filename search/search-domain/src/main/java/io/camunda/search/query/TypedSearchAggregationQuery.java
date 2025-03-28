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
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;

public interface TypedSearchAggregationQuery<F extends FilterBase, A extends AggregationBase>
    extends TypedSearchQuery<F, NoSort> {

  @Override
  default NoSort sort() {
    return NoSort.NO_SORT;
  }

  @Override
  default SearchQueryPage page() {
    return SearchQueryPage.NO_ENTITIES_QUERY;
  }

  A aggregation();
}
