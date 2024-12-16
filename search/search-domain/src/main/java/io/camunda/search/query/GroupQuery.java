/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.GroupSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record GroupQuery(GroupFilter filter, GroupSort sort, SearchQueryPage page)
    implements TypedSearchQuery<GroupFilter, GroupSort> {

  public static GroupQuery of(final Function<GroupQuery.Builder, ObjectBuilder<GroupQuery>> fn) {
    return fn.apply(new GroupQuery.Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<GroupQuery.Builder>
      implements TypedSearchQueryBuilder<GroupQuery, GroupQuery.Builder, GroupFilter, GroupSort> {
    private static final GroupFilter EMPTY_FILTER = FilterBuilders.group().build();
    private static final GroupSort EMPTY_SORT = SortOptionBuilders.group().build();

    private GroupFilter filter;
    private GroupSort sort;

    @Override
    public GroupQuery.Builder filter(final GroupFilter value) {
      filter = value;
      return this;
    }

    @Override
    public GroupQuery.Builder sort(final GroupSort value) {
      sort = value;
      return this;
    }

    public GroupQuery.Builder filter(
        final Function<GroupFilter.Builder, ObjectBuilder<GroupFilter>> fn) {
      return filter(FilterBuilders.group(fn));
    }

    public GroupQuery.Builder sort(final Function<GroupSort.Builder, ObjectBuilder<GroupSort>> fn) {
      return sort(SortOptionBuilders.group(fn));
    }

    @Override
    protected GroupQuery.Builder self() {
      return this;
    }

    @Override
    public GroupQuery build() {
      return new GroupQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
