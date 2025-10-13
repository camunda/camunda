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
import io.camunda.search.sort.GroupMemberSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record GroupMemberQuery(GroupFilter filter, GroupMemberSort sort, SearchQueryPage page)
    implements TypedSearchQuery<GroupFilter, GroupMemberSort> {

  public static GroupMemberQuery of(
      final Function<GroupMemberQuery.Builder, ObjectBuilder<GroupMemberQuery>> fn) {
    return fn.apply(new GroupMemberQuery.Builder()).build();
  }

  public Builder toBuilder() {
    return new Builder().filter(filter).sort(sort).page(page);
  }

  public static final class Builder extends AbstractQueryBuilder<GroupMemberQuery.Builder>
      implements TypedSearchQueryBuilder<
          GroupMemberQuery, GroupMemberQuery.Builder, GroupFilter, GroupMemberSort> {
    private static final GroupFilter EMPTY_FILTER = FilterBuilders.group().build();
    private static final GroupMemberSort EMPTY_SORT = SortOptionBuilders.groupMember().build();

    private GroupFilter filter;
    private GroupMemberSort sort;

    @Override
    public GroupMemberQuery.Builder filter(final GroupFilter value) {
      filter = value;
      return this;
    }

    @Override
    public GroupMemberQuery.Builder sort(final GroupMemberSort value) {
      sort = value;
      return this;
    }

    public GroupMemberQuery.Builder filter(
        final Function<GroupFilter.Builder, ObjectBuilder<GroupFilter>> fn) {
      return filter(FilterBuilders.group(fn));
    }

    public GroupMemberQuery.Builder sort(
        final Function<GroupMemberSort.Builder, ObjectBuilder<GroupMemberSort>> fn) {
      return sort(SortOptionBuilders.groupMember(fn));
    }

    @Override
    protected GroupMemberQuery.Builder self() {
      return this;
    }

    @Override
    public GroupMemberQuery build() {
      return new GroupMemberQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
