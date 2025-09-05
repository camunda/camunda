/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.CorrelatedMessageEntity;
import io.camunda.search.filter.CorrelatedMessagesFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record CorrelatedMessagesDbQuery(
    CorrelatedMessagesFilter filter, DbQuerySorting<CorrelatedMessageEntity> sort, SearchQueryPage page) {

  public static CorrelatedMessagesDbQuery of(
      final Function<Builder, ObjectBuilder<CorrelatedMessagesDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<CorrelatedMessagesDbQuery> {
    
    private static final CorrelatedMessagesFilter EMPTY_FILTER =
        new CorrelatedMessagesFilter.Builder().build();
    
    private CorrelatedMessagesFilter filter;
    private DbQuerySorting<CorrelatedMessageEntity> sort;
    private SearchQueryPage page;

    public Builder filter(final CorrelatedMessagesFilter filter) {
      this.filter = filter;
      return this;
    }

    public Builder sort(final DbQuerySorting<CorrelatedMessageEntity> sort) {
      this.sort = sort;
      return this;
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<CorrelatedMessageEntity>,
                ObjectBuilder<DbQuerySorting<CorrelatedMessageEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    public Builder page(final SearchQueryPage page) {
      this.page = page;
      return this;
    }

    @Override
    public CorrelatedMessagesDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new CorrelatedMessagesDbQuery(filter, sort, page);
    }
  }
}