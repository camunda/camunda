/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record MappingRuleDbQuery(
    MappingRuleFilter filter,
    List<String> authorizedResourceIds,
    DbQuerySorting<MappingRuleEntity> sort,
    DbQueryPage page) {

  public static MappingRuleDbQuery of(
      final Function<MappingRuleDbQuery.Builder, ObjectBuilder<MappingRuleDbQuery>> fn) {
    return fn.apply(new MappingRuleDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<MappingRuleDbQuery> {

    private static final MappingRuleFilter EMPTY_FILTER = FilterBuilders.mappingRule().build();

    private MappingRuleFilter filter;
    private List<String> authorizedResourceIds = java.util.Collections.emptyList();
    private DbQuerySorting<MappingRuleEntity> sort;
    private DbQueryPage page;

    public Builder filter(final MappingRuleFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<MappingRuleEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder filter(
        final Function<MappingRuleFilter.Builder, ObjectBuilder<MappingRuleFilter>> fn) {
      return filter(FilterBuilders.mappingRule(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<MappingRuleEntity>,
                ObjectBuilder<DbQuerySorting<MappingRuleEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public MappingRuleDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      return new MappingRuleDbQuery(filter, authorizedResourceIds, sort, page);
    }
  }
}
