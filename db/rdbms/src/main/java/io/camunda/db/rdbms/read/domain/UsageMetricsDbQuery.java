/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record UsageMetricsDbQuery(UsageMetricsFilter filter, List<String> authorizedTenantIds) {

  public static UsageMetricsDbQuery of(
      final Function<UsageMetricsDbQuery.Builder, ObjectBuilder<UsageMetricsDbQuery>> fn) {
    return fn.apply(new UsageMetricsDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<UsageMetricsDbQuery> {
    private static final UsageMetricsFilter EMPTY_FILTER = FilterBuilders.usageMetrics().build();

    private UsageMetricsFilter filter;
    private List<String> authorizedTenantIds = Collections.emptyList();

    public Builder filter(final UsageMetricsFilter value) {
      filter = value;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder filter(
        final Function<UsageMetricsFilter.Builder, ObjectBuilder<UsageMetricsFilter>> fn) {
      return filter(FilterBuilders.usageMetrics(fn));
    }

    @Override
    public UsageMetricsDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new UsageMetricsDbQuery(filter, authorizedTenantIds);
    }
  }
}
