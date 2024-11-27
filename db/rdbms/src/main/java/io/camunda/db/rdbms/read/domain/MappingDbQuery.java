/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.MappingEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MappingFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record MappingDbQuery(
    MappingFilter filter, DbQuerySorting<MappingEntity> sort, DbQueryPage page) {

  public static MappingDbQuery of(
      final Function<MappingDbQuery.Builder, ObjectBuilder<MappingDbQuery>> fn) {
    return fn.apply(new MappingDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<MappingDbQuery> {

    private static final MappingFilter EMPTY_FILTER = FilterBuilders.mapping().build();

    private MappingFilter filter;
    private DbQuerySorting<MappingEntity> sort;
    private DbQueryPage page;

    public Builder filter(final MappingFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<MappingEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(final Function<MappingFilter.Builder, ObjectBuilder<MappingFilter>> fn) {
      return filter(FilterBuilders.mapping(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<MappingEntity>, ObjectBuilder<DbQuerySorting<MappingEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public MappingDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new MappingDbQuery(filter, sort, page);
    }
  }
}
