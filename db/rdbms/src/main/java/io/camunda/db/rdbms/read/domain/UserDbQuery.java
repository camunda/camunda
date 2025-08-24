/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.UserEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record UserDbQuery(
    UserFilter filter,
    List<String> authorizedResourceIds,
    DbQuerySorting<UserEntity> sort,
    DbQueryPage page) {

  public static UserDbQuery of(final Function<Builder, ObjectBuilder<UserDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<UserDbQuery> {

    private static final UserFilter EMPTY_FILTER = FilterBuilders.user().build();

    private UserFilter filter;
    private DbQuerySorting<UserEntity> sort;
    private DbQueryPage page;
    private List<String> authorizedResourceIds = java.util.Collections.emptyList();

    public UserDbQuery.Builder filter(final UserFilter value) {
      filter = value;
      return this;
    }

    public UserDbQuery.Builder sort(final DbQuerySorting<UserEntity> value) {
      sort = value;
      return this;
    }

    public UserDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public UserDbQuery.Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public UserDbQuery.Builder filter(
        final Function<UserFilter.Builder, ObjectBuilder<UserFilter>> fn) {
      return filter(FilterBuilders.user(fn));
    }

    public UserDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<UserEntity>, ObjectBuilder<DbQuerySorting<UserEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public UserDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      return new UserDbQuery(filter, authorizedResourceIds, sort, page);
    }
  }
}
