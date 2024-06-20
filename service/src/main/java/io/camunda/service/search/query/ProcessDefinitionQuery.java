/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.ProcessDefinitionFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.sort.ProcessDefinitionSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record ProcessDefinitionQuery(
    ProcessDefinitionFilter filter, ProcessDefinitionSort sort, SearchQueryPage page)
    implements TypedSearchQuery<ProcessDefinitionFilter, ProcessDefinitionSort> {

  public static ProcessDefinitionQuery of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements ObjectBuilder<ProcessDefinitionQuery> {

    private static final ProcessDefinitionFilter EMPTY_FILTER =
        FilterBuilders.processDefinition().build();
    private static final ProcessDefinitionSort EMPTY_SORT =
        SortOptionBuilders.processDefinition().build();

    private ProcessDefinitionFilter filter;
    private ProcessDefinitionSort sort;

    public Builder filter(final ProcessDefinitionFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<ProcessDefinitionFilter.Builder, ObjectBuilder<ProcessDefinitionFilter>>
            fn) {
      return filter(FilterBuilders.processDefinition(fn));
    }

    public Builder sort(final ProcessDefinitionSort value) {
      sort = value;
      return this;
    }

    public Builder sort(
        final Function<ProcessDefinitionSort.Builder, ObjectBuilder<ProcessDefinitionSort>> fn) {
      return sort(SortOptionBuilders.processDefinition(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessDefinitionQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new ProcessDefinitionQuery(filter, sort, page());
    }
  }
}
