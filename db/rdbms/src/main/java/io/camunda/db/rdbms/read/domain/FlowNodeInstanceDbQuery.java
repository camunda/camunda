/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record FlowNodeInstanceDbQuery(
    FlowNodeInstanceFilter filter, FlowNodeInstanceSort sort, SearchQueryPage page) {

  public FlowNodeInstanceDbQuery {
    // There should be a default in the SearchQueryPage, so this should never happen
    Objects.requireNonNull(page);
  }

  public static FlowNodeInstanceDbQuery of(
      final Function<FlowNodeInstanceDbQuery.Builder, ObjectBuilder<FlowNodeInstanceDbQuery>> fn) {
    return fn.apply(new FlowNodeInstanceDbQuery.Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<FlowNodeInstanceDbQuery.Builder>
      implements ObjectBuilder<FlowNodeInstanceDbQuery> {

    private static final FlowNodeInstanceFilter EMPTY_FILTER =
        FilterBuilders.flowNodeInstance().build();
    private static final FlowNodeInstanceSort EMPTY_SORT =
        SortOptionBuilders.flowNodeInstance().build();

    private FlowNodeInstanceFilter filter;
    private FlowNodeInstanceSort sort;

    public Builder filter(final FlowNodeInstanceFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final FlowNodeInstanceSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<FlowNodeInstanceFilter.Builder, ObjectBuilder<FlowNodeInstanceFilter>> fn) {
      return filter(FilterBuilders.flowNodeInstance(fn));
    }

    public Builder sort(
        final Function<FlowNodeInstanceSort.Builder, ObjectBuilder<FlowNodeInstanceSort>> fn) {
      return sort(SortOptionBuilders.flowNodeInstance(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public FlowNodeInstanceDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new FlowNodeInstanceDbQuery(filter, sort, page().sanitize());
    }
  }
}
