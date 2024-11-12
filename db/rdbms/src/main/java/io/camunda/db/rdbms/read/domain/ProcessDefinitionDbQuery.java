/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.search.sort.ProcessDefinitionSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ProcessDefinitionDbQuery(
    ProcessDefinitionFilter filter, ProcessDefinitionSort sort, SearchQueryPage page) {

  public ProcessDefinitionDbQuery {
    // There should be a default in the SearchQueryPage, so this should never happen
    Objects.requireNonNull(page);
  }

  public static ProcessDefinitionDbQuery of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements ObjectBuilder<ProcessDefinitionDbQuery> {

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

    public Builder sort(final ProcessDefinitionSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<ProcessDefinitionFilter.Builder, ObjectBuilder<ProcessDefinitionFilter>>
            fn) {
      return filter(FilterBuilders.processDefinition(fn));
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
    public ProcessDefinitionDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new ProcessDefinitionDbQuery(filter, sort, page().sanitize());
    }
  }
}
