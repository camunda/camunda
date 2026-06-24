/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FormFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.FormSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record FormQuery(FormFilter filter, FormSort sort, SearchQueryPage page)
    implements TypedSearchQuery<FormFilter, FormSort> {

  public static FormQuery of(final Function<Builder, ObjectBuilder<FormQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<FormQuery, Builder, FormFilter, FormSort> {

    private static final FormFilter EMPTY_FILTER = FilterBuilders.form().build();
    private static final FormSort EMPTY_SORT = SortOptionBuilders.form().build();

    private FormFilter filter;
    private FormSort sort;

    @Override
    public Builder filter(final FormFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final FormSort value) {
      sort = value;
      return this;
    }

    public Builder filter(final Function<FormFilter.Builder, ObjectBuilder<FormFilter>> fn) {
      return filter(FilterBuilders.form(fn));
    }

    public Builder sort(final Function<FormSort.Builder, ObjectBuilder<FormSort>> fn) {
      return sort(SortOptionBuilders.form(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public FormQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new FormQuery(filter, sort, page());
    }
  }
}
