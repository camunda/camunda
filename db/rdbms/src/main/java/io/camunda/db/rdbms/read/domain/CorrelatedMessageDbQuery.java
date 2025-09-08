/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.CorrelatedMessageEntity;
import io.camunda.search.filter.CorrelatedMessageFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record CorrelatedMessageDbQuery(
    CorrelatedMessageFilter filter,
    DbQuerySorting<CorrelatedMessageEntity> sort,
    DbQueryPage page) {

  public static CorrelatedMessageDbQuery of(
      final Function<Builder, ObjectBuilder<CorrelatedMessageDbQuery>> fn) {
    return fn.apply(new CorrelatedMessageDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<CorrelatedMessageDbQuery> {
    private static final CorrelatedMessageFilter EMPTY_FILTER =
        FilterBuilders.correlatedMessage().build();

    private CorrelatedMessageFilter filter;
    private DbQuerySorting<CorrelatedMessageEntity> sort;
    private DbQueryPage page;

    public Builder filter(final CorrelatedMessageFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<CorrelatedMessageEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<CorrelatedMessageFilter.Builder, ObjectBuilder<CorrelatedMessageFilter>>
            fn) {
      return filter(FilterBuilders.correlatedMessage(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<CorrelatedMessageEntity>,
                ObjectBuilder<DbQuerySorting<CorrelatedMessageEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public CorrelatedMessageDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new CorrelatedMessageDbQuery(filter, sort, page);
    }
  }
}
