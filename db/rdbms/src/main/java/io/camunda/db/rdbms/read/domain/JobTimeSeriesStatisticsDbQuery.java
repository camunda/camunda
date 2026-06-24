/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.db.rdbms.sql.columns.JobMetricsBatchColumn;
import io.camunda.search.entities.JobTimeSeriesStatisticsEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobTimeSeriesStatisticsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record JobTimeSeriesStatisticsDbQuery(
    JobTimeSeriesStatisticsFilter filter,
    List<String> authorizedTenantIds,
    long resolutionSeconds,
    String timeBucketColumn,
    DbQueryPage page,
    DbQuerySorting<JobTimeSeriesStatisticsEntity> sort) {

  public static JobTimeSeriesStatisticsDbQuery of(
      final Function<
              JobTimeSeriesStatisticsDbQuery.Builder, ObjectBuilder<JobTimeSeriesStatisticsDbQuery>>
          fn) {
    return fn.apply(new JobTimeSeriesStatisticsDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobTimeSeriesStatisticsDbQuery> {
    private JobTimeSeriesStatisticsFilter filter;
    private List<String> authorizedTenantIds;
    private DbQueryPage page;
    private DbQuerySorting<JobTimeSeriesStatisticsEntity> sort;

    public Builder filter(final JobTimeSeriesStatisticsFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<
                JobTimeSeriesStatisticsFilter.Builder, ObjectBuilder<JobTimeSeriesStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.jobTimeSeriesStatistics(fn));
    }

    public Builder authorizedTenantIds(final List<String> value) {
      authorizedTenantIds = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<JobTimeSeriesStatisticsEntity> value) {
      sort = value;
      return this;
    }

    @Override
    public JobTimeSeriesStatisticsDbQuery build() {
      final var f = Objects.requireNonNull(filter, "filter must not be null");
      final long resolutionSeconds = Math.max(1L, f.resolution().getSeconds());
      return new JobTimeSeriesStatisticsDbQuery(
          f,
          Objects.requireNonNullElse(authorizedTenantIds, Collections.emptyList()),
          resolutionSeconds,
          JobMetricsBatchColumn.START_TIME.name(),
          page,
          sort);
    }
  }
}
