/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.JobEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record JobDbQuery(
    JobFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<JobEntity> sort,
    DbQueryPage page) {

  public static JobDbQuery of(final Function<Builder, ObjectBuilder<JobDbQuery>> fn) {
    return fn.apply(new JobDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobDbQuery> {

    private static final JobFilter EMPTY_FILTER = FilterBuilders.job().build();

    private JobFilter filter;
    private List<String> authorizedResourceIds = List.of();
    private List<String> authorizedTenantIds = List.of();
    private DbQuerySorting<JobEntity> sort;
    private DbQueryPage page;

    public Builder filter(final JobFilter value) {
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

    public Builder sort(final DbQuerySorting<JobEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public JobDbQuery.Builder filter(
        final Function<JobFilter.Builder, ObjectBuilder<JobFilter>> fn) {
      return filter(FilterBuilders.job(fn));
    }

    public JobDbQuery.Builder sort(
        final Function<DbQuerySorting.Builder<JobEntity>, ObjectBuilder<DbQuerySorting<JobEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public JobDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new JobDbQuery(filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
