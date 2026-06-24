/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobTypeStatisticsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record JobTypeStatisticsDbQuery(
    JobTypeStatisticsFilter filter,
    List<String> authorizedTenantIds,
    DbQueryPage page,
    DbQuerySorting<JobTypeStatisticsEntity> sort) {

  public static JobTypeStatisticsDbQuery of(
      final Function<JobTypeStatisticsDbQuery.Builder, ObjectBuilder<JobTypeStatisticsDbQuery>>
          fn) {
    return fn.apply(new JobTypeStatisticsDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobTypeStatisticsDbQuery> {
    private JobTypeStatisticsFilter filter;
    private List<String> authorizedTenantIds;
    private DbQueryPage page;
    private DbQuerySorting<JobTypeStatisticsEntity> sort;

    public Builder filter(final JobTypeStatisticsFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<JobTypeStatisticsFilter.Builder, ObjectBuilder<JobTypeStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.jobTypeStatistics(fn));
    }

    public Builder authorizedTenantIds(final List<String> value) {
      authorizedTenantIds = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<JobTypeStatisticsEntity> value) {
      sort = value;
      return this;
    }

    @Override
    public JobTypeStatisticsDbQuery build() {
      Objects.requireNonNull(filter, "filter must not be null");
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedTenantIds =
          Objects.requireNonNullElse(authorizedTenantIds, Collections.emptyList());
      return new JobTypeStatisticsDbQuery(filter, authorizedTenantIds, page, sort);
    }
  }
}
