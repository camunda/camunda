/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GlobalListenerFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

public record GlobalListenerDbQuery(
    GlobalListenerFilter filter, DbQuerySorting<GlobalListenerEntity> sort, DbQueryPage page) {

  public static GlobalListenerDbQuery of(
      final Function<GlobalListenerDbQuery.Builder, ObjectBuilder<GlobalListenerDbQuery>> fn) {
    return fn.apply(new GlobalListenerDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<GlobalListenerDbQuery> {

    private static final GlobalListenerFilter EMPTY_FILTER =
        FilterBuilders.globalListener().build();

    private GlobalListenerFilter filter;
    private DbQuerySorting<GlobalListenerEntity> sort;
    private DbQueryPage page;

    public Builder filter(final GlobalListenerFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<GlobalListenerEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<GlobalListenerFilter.Builder, ObjectBuilder<GlobalListenerFilter>> fn) {
      return filter(FilterBuilders.globalListener(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<GlobalListenerEntity>,
                ObjectBuilder<DbQuerySorting<GlobalListenerEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public GlobalListenerDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(Collections.emptyList()));
      return new GlobalListenerDbQuery(filter, sort, page);
    }
  }
}
