/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AuthorizationDbQuery(
    AuthorizationFilter filter,
    List<String> authorizedResourceIds,
    DbQuerySorting<AuthorizationEntity> sort,
    DbQueryPage page) {

  public static AuthorizationDbQuery of(
      final Function<Builder, ObjectBuilder<AuthorizationDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<AuthorizationDbQuery> {

    private static final AuthorizationFilter EMPTY_FILTER = FilterBuilders.authorization().build();

    private AuthorizationFilter filter;
    private List<String> authorizedResourceIds = List.of();
    private DbQuerySorting<AuthorizationEntity> sort;
    private DbQueryPage page;

    public AuthorizationDbQuery.Builder filter(final AuthorizationFilter value) {
      filter = value;
      return this;
    }

    public AuthorizationDbQuery.Builder authorizedResourceIds(
        final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public AuthorizationDbQuery.Builder sort(final DbQuerySorting<AuthorizationEntity> value) {
      sort = value;
      return this;
    }

    public AuthorizationDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public AuthorizationDbQuery.Builder filter(
        final Function<AuthorizationFilter.Builder, ObjectBuilder<AuthorizationFilter>> fn) {
      return filter(FilterBuilders.authorization(fn));
    }

    public AuthorizationDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<AuthorizationEntity>,
                ObjectBuilder<DbQuerySorting<AuthorizationEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public AuthorizationDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      return new AuthorizationDbQuery(filter, authorizedResourceIds, sort, page);
    }
  }
}
