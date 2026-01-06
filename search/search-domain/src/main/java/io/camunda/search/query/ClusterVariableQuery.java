/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.search.query;

import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ClusterVariableSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record ClusterVariableQuery(
    ClusterVariableFilter filter, ClusterVariableSort sort, SearchQueryPage page)
    implements TypedSearchQuery<ClusterVariableFilter, ClusterVariableSort> {

  public static ClusterVariableQuery of(
      final Function<Builder, ObjectBuilder<ClusterVariableQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          ClusterVariableQuery, Builder, ClusterVariableFilter, ClusterVariableSort> {

    private static final ClusterVariableFilter EMPTY_FILTER =
        FilterBuilders.clusterVariable().build();
    private static final ClusterVariableSort EMPTY_SORT =
        SortOptionBuilders.clusterVariable().build();

    private ClusterVariableFilter filter;
    private ClusterVariableSort sort;

    @Override
    public Builder filter(final ClusterVariableFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final ClusterVariableSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<ClusterVariableFilter.Builder, ObjectBuilder<ClusterVariableFilter>> fn) {
      return filter(FilterBuilders.clusterVariable(fn));
    }

    public Builder sort(
        final Function<ClusterVariableSort.Builder, ObjectBuilder<ClusterVariableSort>> fn) {
      return sort(SortOptionBuilders.clusterVariable(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ClusterVariableQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new ClusterVariableQuery(filter, sort, page());
    }
  }
}
