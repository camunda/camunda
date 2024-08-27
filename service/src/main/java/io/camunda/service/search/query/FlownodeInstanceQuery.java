/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.FlownodeInstanceFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.sort.FlownodeInstanceSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import java.util.Objects;

public record FlownodeInstanceQuery(
    FlownodeInstanceFilter filter, FlownodeInstanceSort sort, SearchQueryPage page)
    implements TypedSearchQuery<FlownodeInstanceFilter, FlownodeInstanceSort> {

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          FlownodeInstanceQuery, Builder, FlownodeInstanceFilter, FlownodeInstanceSort> {

    private static final FlownodeInstanceFilter EMPTY_FILTER =
        FilterBuilders.flownodeInstance().build();
    private static final FlownodeInstanceSort EMPTY_SORT =
        SortOptionBuilders.flownodeInstance().build();

    private FlownodeInstanceFilter filter;
    private FlownodeInstanceSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final FlownodeInstanceFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final FlownodeInstanceSort value) {
      sort = value;
      return this;
    }

    @Override
    public FlownodeInstanceQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new FlownodeInstanceQuery(filter, sort, page());
    }
  }
}
