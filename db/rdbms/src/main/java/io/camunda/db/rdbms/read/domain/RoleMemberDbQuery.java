/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.RoleMemberFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record RoleMemberDbQuery(
    RoleMemberFilter filter,
    List<String> authorizedResourceIds,
    DbQuerySorting<RoleMemberEntity> sort,
    DbQueryPage page) {

  public static RoleMemberDbQuery of(final Function<Builder, ObjectBuilder<RoleMemberDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<RoleMemberDbQuery> {

    private static final RoleMemberFilter EMPTY_FILTER = FilterBuilders.roleMember().build();

    private RoleMemberFilter filter;
    private List<String> authorizedResourceIds = Collections.emptyList();
    private DbQuerySorting<RoleMemberEntity> sort;
    private DbQueryPage page;

    public RoleMemberDbQuery.Builder filter(final RoleMemberFilter value) {
      filter = value;
      return this;
    }

    public RoleMemberDbQuery.Builder authorizedResourceIds(
        final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public RoleMemberDbQuery.Builder sort(final DbQuerySorting<RoleMemberEntity> value) {
      sort = value;
      return this;
    }

    public RoleMemberDbQuery.Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public RoleMemberDbQuery.Builder filter(
        final Function<RoleMemberFilter.Builder, ObjectBuilder<RoleMemberFilter>> fn) {
      return filter(FilterBuilders.roleMember(fn));
    }

    public RoleMemberDbQuery.Builder sort(
        final Function<
                DbQuerySorting.Builder<RoleMemberEntity>,
                ObjectBuilder<DbQuerySorting<RoleMemberEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public RoleMemberDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new RoleMemberDbQuery(filter, authorizedResourceIds, sort, page);
    }
  }
}
