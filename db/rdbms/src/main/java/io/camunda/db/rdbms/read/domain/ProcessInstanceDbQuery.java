/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ProcessInstanceDbQuery(
    ProcessInstanceFilter filter, ProcessInstanceSort sort, SearchQueryPage page) {

  public ProcessInstanceDbQuery {
    // There should be a default in the SearchQueryPage, so this should never happen
    Objects.requireNonNull(page);
  }

  public static ProcessInstanceDbQuery of(
      final Function<ProcessInstanceDbQuery.Builder, ObjectBuilder<ProcessInstanceDbQuery>> fn) {
    return fn.apply(new ProcessInstanceDbQuery.Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<ProcessInstanceDbQuery.Builder>
      implements ObjectBuilder<ProcessInstanceDbQuery> {

    private static final ProcessInstanceFilter EMPTY_FILTER =
        FilterBuilders.processInstance().build();
    private static final ProcessInstanceSort EMPTY_SORT =
        SortOptionBuilders.processInstance().build();

    private ProcessInstanceFilter filter;
    private ProcessInstanceSort sort;

    public Builder filter(final ProcessInstanceFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final ProcessInstanceSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<ProcessInstanceFilter.Builder, ObjectBuilder<ProcessInstanceFilter>> fn) {
      return filter(FilterBuilders.processInstance(fn));
    }

    public Builder sort(
        final Function<ProcessInstanceSort.Builder, ObjectBuilder<ProcessInstanceSort>> fn) {
      return sort(SortOptionBuilders.processInstance(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessInstanceDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      final var page = page() != null ? page().sanitize() : SearchQueryPage.DEFAULT;
      return new ProcessInstanceDbQuery(filter, sort, page);
    }
  }
}
