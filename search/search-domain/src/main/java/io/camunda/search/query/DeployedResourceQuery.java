/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.DeployedResourceFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.DeployedResourceSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record DeployedResourceQuery(
    DeployedResourceFilter filter, DeployedResourceSort sort, SearchQueryPage page)
    implements TypedSearchQuery<DeployedResourceFilter, DeployedResourceSort> {

  public static DeployedResourceQuery of(
      final Function<Builder, ObjectBuilder<DeployedResourceQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          DeployedResourceQuery, Builder, DeployedResourceFilter, DeployedResourceSort> {

    private static final DeployedResourceFilter EMPTY_FILTER =
        FilterBuilders.deployedResource().build();
    private static final DeployedResourceSort EMPTY_SORT =
        SortOptionBuilders.deployedResource().build();

    private DeployedResourceFilter filter;
    private DeployedResourceSort sort;

    @Override
    public Builder filter(final DeployedResourceFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final DeployedResourceSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<DeployedResourceFilter.Builder, ObjectBuilder<DeployedResourceFilter>> fn) {
      return filter(FilterBuilders.deployedResource(fn));
    }

    public Builder sort(
        final Function<DeployedResourceSort.Builder, ObjectBuilder<DeployedResourceSort>> fn) {
      return sort(SortOptionBuilders.deployedResource(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DeployedResourceQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new DeployedResourceQuery(filter, sort, page());
    }
  }
}
