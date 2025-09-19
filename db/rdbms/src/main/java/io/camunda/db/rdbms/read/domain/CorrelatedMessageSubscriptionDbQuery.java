/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.db.rdbms.read.domain.MessageSubscriptionDbQuery.Builder;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record CorrelatedMessageSubscriptionDbQuery(
    CorrelatedMessageSubscriptionFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<CorrelatedMessageSubscriptionEntity> sort,
    DbQueryPage page) {

  public static CorrelatedMessageSubscriptionDbQuery of(
      final Function<Builder, ObjectBuilder<CorrelatedMessageSubscriptionDbQuery>> fn) {
    return fn.apply(new CorrelatedMessageSubscriptionDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<CorrelatedMessageSubscriptionDbQuery> {
    private static final CorrelatedMessageSubscriptionFilter EMPTY_FILTER =
        FilterBuilders.correlatedMessageSubscription().build();

    private CorrelatedMessageSubscriptionFilter filter;
    private List<String> authorizedResourceIds = java.util.Collections.emptyList();
    private List<String> authorizedTenantIds = java.util.Collections.emptyList();
    private DbQuerySorting<CorrelatedMessageSubscriptionEntity> sort;
    private DbQueryPage page;

    public Builder filter(final CorrelatedMessageSubscriptionFilter value) {
      filter = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder sort(final DbQuerySorting<CorrelatedMessageSubscriptionEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<
                CorrelatedMessageSubscriptionFilter.Builder,
                ObjectBuilder<CorrelatedMessageSubscriptionFilter>>
            fn) {
      return filter(FilterBuilders.correlatedMessageSubscription(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<CorrelatedMessageSubscriptionEntity>,
                ObjectBuilder<DbQuerySorting<CorrelatedMessageSubscriptionEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public CorrelatedMessageSubscriptionDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new CorrelatedMessageSubscriptionDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
