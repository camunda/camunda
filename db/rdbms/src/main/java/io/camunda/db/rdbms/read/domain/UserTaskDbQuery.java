/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record UserTaskDbQuery(
    UserTaskFilter filter,
    List<String> authorizedProcessDefinitionIds,
    List<Long> authorizedUserTaskKeys,
    List<String> authorizedTenantIds,
    DbQuerySorting<UserTaskEntity> sort,
    DbQueryPage page) {

  public static UserTaskDbQuery of(
      final Function<UserTaskDbQuery.Builder, ObjectBuilder<UserTaskDbQuery>> fn) {
    return fn.apply(new UserTaskDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<UserTaskDbQuery> {

    private static final UserTaskFilter EMPTY_FILTER = FilterBuilders.userTask().build();

    private UserTaskFilter filter;
    private List<String> authorizedProcessDefinitionIds = java.util.Collections.emptyList();
    private List<Long> authorizedUserTaskKeys = java.util.Collections.emptyList();
    private List<String> authorizedTenantIds = java.util.Collections.emptyList();
    private DbQuerySorting<UserTaskEntity> sort;
    private DbQueryPage page;

    public UserTaskDbQuery.Builder filter(final UserTaskFilter value) {
      filter = value;
      return this;
    }

    public UserTaskDbQuery.Builder sort(final DbQuerySorting<UserTaskEntity> value) {
      sort = value;
      return this;
    }

    public UserTaskDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public UserTaskDbQuery.Builder authorizedProcessDefinitionIds(
        final List<String> authorizedProcessDefinitionIds) {
      this.authorizedProcessDefinitionIds = authorizedProcessDefinitionIds;
      return this;
    }

    public UserTaskDbQuery.Builder authorizedUserTaskKeys(final List<Long> authorizedUserTaskKeys) {
      this.authorizedUserTaskKeys = authorizedUserTaskKeys;
      return this;
    }

    public UserTaskDbQuery.Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public UserTaskDbQuery.Builder filter(
        final Function<UserTaskFilter.Builder, ObjectBuilder<UserTaskFilter>> fn) {
      return filter(FilterBuilders.userTask(fn));
    }

    public UserTaskDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<UserTaskEntity>,
                ObjectBuilder<DbQuerySorting<UserTaskEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public UserTaskDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedProcessDefinitionIds =
          Objects.requireNonNullElse(authorizedProcessDefinitionIds, List.of());
      authorizedUserTaskKeys = Objects.requireNonNullElse(authorizedUserTaskKeys, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new UserTaskDbQuery(
          filter,
          authorizedProcessDefinitionIds,
          authorizedUserTaskKeys,
          authorizedTenantIds,
          sort,
          page);
    }
  }
}
