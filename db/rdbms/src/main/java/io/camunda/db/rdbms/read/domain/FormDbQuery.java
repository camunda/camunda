/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.FormEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FormFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record FormDbQuery(FormFilter filter, DbQuerySorting<FormEntity> sort, DbQueryPage page) {

  public static FormDbQuery of(final Function<FormDbQuery.Builder, ObjectBuilder<FormDbQuery>> fn) {
    return fn.apply(new FormDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<FormDbQuery> {

    private static final FormFilter EMPTY_FILTER = FilterBuilders.form().build();

    private FormFilter filter;
    private DbQuerySorting<FormEntity> sort;
    private DbQueryPage page;

    public Builder filter(final FormFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<FormEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(final Function<FormFilter.Builder, ObjectBuilder<FormFilter>> fn) {
      return filter(FilterBuilders.form(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<FormEntity>, ObjectBuilder<DbQuerySorting<FormEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public FormDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new FormDbQuery(filter, sort, page);
    }
  }
}
