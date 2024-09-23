/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.ProcessInstanceFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.result.ProcessInstanceQueryResultConfig;
import io.camunda.service.search.result.QueryResultConfig;
import io.camunda.service.search.result.QueryResultConfigBuilders;
import io.camunda.service.search.sort.ProcessInstanceSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ProcessInstanceQuery(
    ProcessInstanceFilter filter,
    ProcessInstanceSort sort,
    SearchQueryPage page,
    QueryResultConfig resultConfig)
    implements TypedSearchQuery<ProcessInstanceFilter, ProcessInstanceSort> {

  public static ProcessInstanceQuery of(
      final Function<Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          ProcessInstanceQuery, Builder, ProcessInstanceFilter, ProcessInstanceSort> {

    private static final ProcessInstanceFilter EMPTY_FILTER =
        FilterBuilders.processInstance().build();
    private static final ProcessInstanceSort EMPTY_SORT =
        SortOptionBuilders.processInstance().build();

    private ProcessInstanceFilter filter;
    private ProcessInstanceSort sort;
    private ProcessInstanceQueryResultConfig resultConfig;

    public Builder filter(final ProcessInstanceFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<ProcessInstanceFilter.Builder, ObjectBuilder<ProcessInstanceFilter>> fn) {
      return filter(FilterBuilders.processInstance(fn));
    }

    public Builder sort(final ProcessInstanceSort value) {
      sort = value;
      return this;
    }

    public Builder sort(
        final Function<ProcessInstanceSort.Builder, ObjectBuilder<ProcessInstanceSort>> fn) {
      return sort(SortOptionBuilders.processInstance(fn));
    }

    public Builder resultConfig(final ProcessInstanceQueryResultConfig value) {
      resultConfig = value;
      return this;
    }

    public Builder resultConfig(
        final Function<
                ProcessInstanceQueryResultConfig.Builder,
                ObjectBuilder<ProcessInstanceQueryResultConfig>>
            fn) {
      return resultConfig(QueryResultConfigBuilders.processInstance(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessInstanceQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new ProcessInstanceQuery(filter, sort, page(), resultConfig);
    }
  }
}
