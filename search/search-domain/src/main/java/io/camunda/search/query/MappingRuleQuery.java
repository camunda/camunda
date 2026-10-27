/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.MappingRuleSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record MappingRuleQuery(MappingRuleFilter filter, MappingRuleSort sort, SearchQueryPage page)
    implements TypedSearchQuery<MappingRuleFilter, MappingRuleSort> {
  public static MappingRuleQuery of(final Function<Builder, ObjectBuilder<MappingRuleQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public MappingRuleQuery.Builder toBuilder() {
    return new MappingRuleQuery.Builder().filter(filter).sort(sort).page(page);
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          MappingRuleQuery, MappingRuleQuery.Builder, MappingRuleFilter, MappingRuleSort> {
    private static final MappingRuleFilter EMPTY_FILTER = FilterBuilders.mappingRule().build();
    private static final MappingRuleSort EMPTY_SORT = SortOptionBuilders.mappingRule().build();

    private MappingRuleFilter filter;
    private MappingRuleSort sort;

    @Override
    public Builder filter(final MappingRuleFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final MappingRuleSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<MappingRuleFilter.Builder, ObjectBuilder<MappingRuleFilter>> fn) {
      return filter(FilterBuilders.mappingRule(fn));
    }

    public Builder sort(
        final Function<MappingRuleSort.Builder, ObjectBuilder<MappingRuleSort>> fn) {
      return sort(SortOptionBuilders.mappingRule(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public MappingRuleQuery build() {
      return new MappingRuleQuery(
          Objects.requireNonNullElse(filter, EMPTY_FILTER),
          Objects.requireNonNullElse(sort, EMPTY_SORT),
          page());
    }
  }
}
