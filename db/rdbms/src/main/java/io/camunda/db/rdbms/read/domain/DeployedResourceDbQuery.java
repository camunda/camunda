/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.filter.DeployedResourceFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record DeployedResourceDbQuery(
    DeployedResourceFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<DeployedResourceEntity> sort,
    DbQueryPage page) {

  public static DeployedResourceDbQuery of(
      final Function<Builder, ObjectBuilder<DeployedResourceDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<DeployedResourceDbQuery> {

    private static final DeployedResourceFilter EMPTY_FILTER =
        FilterBuilders.deployedResource().build();

    private DeployedResourceFilter filter;
    private List<String> authorizedResourceIds = java.util.Collections.emptyList();
    private List<String> authorizedTenantIds = java.util.Collections.emptyList();
    private DbQuerySorting<DeployedResourceEntity> sort;
    private DbQueryPage page;

    public Builder filter(final DeployedResourceFilter value) {
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

    public Builder sort(final DbQuerySorting<DeployedResourceEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<DeployedResourceFilter.Builder, ObjectBuilder<DeployedResourceFilter>> fn) {
      return filter(FilterBuilders.deployedResource(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<DeployedResourceEntity>,
                ObjectBuilder<DbQuerySorting<DeployedResourceEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public DeployedResourceDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new DeployedResourceDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
