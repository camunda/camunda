/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.search.query;

import io.camunda.search.filter.CaseFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.CaseSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final record CaseQuery(CaseFilter filter, CaseSort sort, SearchQueryPage page)
    implements TypedSearchQuery<CaseFilter, CaseSort> {

  public static CaseQuery of(final Function<Builder, ObjectBuilder<CaseQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<CaseQuery, Builder, CaseFilter, CaseSort> {

    private static final CaseFilter EMPTY_FILTER = FilterBuilders.caseFilter().build();
    private static final CaseSort EMPTY_SORT = SortOptionBuilders.caseSort().build();

    private @Nullable CaseFilter filter;
    private @Nullable CaseSort sort;

    @Override
    public Builder filter(final CaseFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final CaseSort value) {
      sort = value;
      return this;
    }

    public Builder filter(final Function<CaseFilter.Builder, ObjectBuilder<CaseFilter>> fn) {
      return filter(FilterBuilders.caseFilter(fn));
    }

    public Builder sort(final Function<CaseSort.Builder, ObjectBuilder<CaseSort>> fn) {
      return sort(SortOptionBuilders.caseSort(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public CaseQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new CaseQuery(filter, sort, page());
    }
  }
}
