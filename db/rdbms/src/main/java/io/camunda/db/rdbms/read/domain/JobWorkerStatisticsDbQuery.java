/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.JobWorkerStatisticsEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobWorkerStatisticsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record JobWorkerStatisticsDbQuery(
    JobWorkerStatisticsFilter filter,
    List<String> authorizedTenantIds,
    DbQueryPage page,
    DbQuerySorting<JobWorkerStatisticsEntity> sort) {

  public static JobWorkerStatisticsDbQuery of(
      final Function<JobWorkerStatisticsDbQuery.Builder, ObjectBuilder<JobWorkerStatisticsDbQuery>>
          fn) {
    return fn.apply(new JobWorkerStatisticsDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobWorkerStatisticsDbQuery> {
    private JobWorkerStatisticsFilter filter;
    private List<String> authorizedTenantIds;
    private DbQueryPage page;
    private DbQuerySorting<JobWorkerStatisticsEntity> sort;

    public Builder filter(final JobWorkerStatisticsFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<JobWorkerStatisticsFilter.Builder, ObjectBuilder<JobWorkerStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.jobWorkerStatistics(fn));
    }

    public Builder authorizedTenantIds(final List<String> value) {
      authorizedTenantIds = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<JobWorkerStatisticsEntity> value) {
      sort = value;
      return this;
    }

    @Override
    public JobWorkerStatisticsDbQuery build() {
      return new JobWorkerStatisticsDbQuery(
          Objects.requireNonNull(filter, "filter must not be null"),
          Objects.requireNonNullElse(authorizedTenantIds, Collections.emptyList()),
          page,
          sort);
    }
  }
}
