/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GlobalJobStatisticsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record GlobalJobStatisticsDbQuery(
    GlobalJobStatisticsFilter filter, List<String> authorizedTenantIds) {

  public static GlobalJobStatisticsDbQuery of(
      final Function<GlobalJobStatisticsDbQuery.Builder, ObjectBuilder<GlobalJobStatisticsDbQuery>>
          fn) {
    return fn.apply(new GlobalJobStatisticsDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<GlobalJobStatisticsDbQuery> {
    private static final GlobalJobStatisticsFilter EMPTY_FILTER =
        FilterBuilders.globalJobStatistics().build();

    private GlobalJobStatisticsFilter filter;
    private List<String> authorizedTenantIds = Collections.emptyList();

    public Builder filter(final GlobalJobStatisticsFilter value) {
      filter = value;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder filter(
        final Function<GlobalJobStatisticsFilter.Builder, ObjectBuilder<GlobalJobStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.globalJobStatistics(fn));
    }

    @Override
    public GlobalJobStatisticsDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new GlobalJobStatisticsDbQuery(filter, authorizedTenantIds);
    }
  }
}
