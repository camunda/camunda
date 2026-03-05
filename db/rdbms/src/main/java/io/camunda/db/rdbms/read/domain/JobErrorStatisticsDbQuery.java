/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.JobErrorStatisticsEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobErrorStatisticsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record JobErrorStatisticsDbQuery(
    JobErrorStatisticsFilter filter,
    List<String> authorizedTenantIds,
    DbQueryPage page,
    DbQuerySorting<JobErrorStatisticsEntity> sort) {

  public static JobErrorStatisticsDbQuery of(
      final Function<JobErrorStatisticsDbQuery.Builder, ObjectBuilder<JobErrorStatisticsDbQuery>>
          fn) {
    return fn.apply(new JobErrorStatisticsDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobErrorStatisticsDbQuery> {
    private JobErrorStatisticsFilter filter;
    private List<String> authorizedTenantIds;
    private DbQueryPage page;
    private DbQuerySorting<JobErrorStatisticsEntity> sort;

    public Builder filter(final JobErrorStatisticsFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<JobErrorStatisticsFilter.Builder, ObjectBuilder<JobErrorStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.jobErrorStatistics(fn));
    }

    public Builder authorizedTenantIds(final List<String> value) {
      authorizedTenantIds = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<JobErrorStatisticsEntity> value) {
      sort = value;
      return this;
    }

    @Override
    public JobErrorStatisticsDbQuery build() {
      return new JobErrorStatisticsDbQuery(
          Objects.requireNonNull(filter, "filter must not be null"),
          Objects.requireNonNullElse(authorizedTenantIds, Collections.emptyList()),
          page,
          sort);
    }
  }
}
