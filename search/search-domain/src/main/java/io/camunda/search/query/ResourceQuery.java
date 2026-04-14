/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ResourceFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ResourceSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ResourceQuery(ResourceFilter filter, ResourceSort sort, SearchQueryPage page)
    implements TypedSearchQuery<ResourceFilter, ResourceSort> {

  public static ResourceQuery of(final Function<Builder, ObjectBuilder<ResourceQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<ResourceQuery, Builder, ResourceFilter, ResourceSort> {

    private static final ResourceFilter EMPTY_FILTER = FilterBuilders.resource().build();
    private static final ResourceSort EMPTY_SORT = SortOptionBuilders.resource().build();

    private ResourceFilter filter;
    private ResourceSort sort;

    @Override
    public Builder filter(final ResourceFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final ResourceSort value) {
      sort = value;
      return this;
    }

    public Builder filter(final Function<ResourceFilter.Builder, ObjectBuilder<ResourceFilter>> fn) {
      return filter(FilterBuilders.resource(fn));
    }

    public Builder sort(final Function<ResourceSort.Builder, ObjectBuilder<ResourceSort>> fn) {
      return sort(SortOptionBuilders.resource(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ResourceQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new ResourceQuery(filter, sort, page());
    }
  }
}
