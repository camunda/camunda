/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MappingFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.UserQuery.Builder;
import io.camunda.search.sort.MappingSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record MappingQuery(MappingFilter filter, MappingSort sort, SearchQueryPage page)
    implements TypedSearchQuery<MappingFilter, MappingSort> {
  public static MappingQuery of(final Function<Builder, ObjectBuilder<MappingQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public MappingQuery.Builder toBuilder() {
    return new MappingQuery.Builder().filter(filter).sort(sort).page(page);
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          MappingQuery, MappingQuery.Builder, MappingFilter, MappingSort> {
    private static final MappingFilter EMPTY_FILTER = FilterBuilders.mapping().build();
    private static final MappingSort EMPTY_SORT = SortOptionBuilders.mapping().build();

    private MappingFilter filter;
    private MappingSort sort;

    @Override
    public Builder filter(final MappingFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final MappingSort value) {
      sort = value;
      return this;
    }

    public Builder filter(final Function<MappingFilter.Builder, ObjectBuilder<MappingFilter>> fn) {
      return filter(FilterBuilders.mapping(fn));
    }

    public Builder sort(final Function<MappingSort.Builder, ObjectBuilder<MappingSort>> fn) {
      return sort(SortOptionBuilders.mapping(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public MappingQuery build() {
      return new MappingQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
