/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.UsageMetricsAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record UsageMetricsQuery(UsageMetricsFilter filter)
    implements TypedSearchAggregationQuery<UsageMetricsFilter, UsageMetricsAggregation> {

  public static UsageMetricsQuery of(final Function<Builder, ObjectBuilder<UsageMetricsQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public UsageMetricsAggregation aggregation() {
    return new UsageMetricsAggregation(filter);
  }

  public static final class Builder implements ObjectBuilder<UsageMetricsQuery> {
    private static final UsageMetricsFilter EMPTY_FILTER = FilterBuilders.usageMetrics().build();

    private UsageMetricsFilter filter;

    public Builder filter(final UsageMetricsFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<UsageMetricsFilter.Builder, ObjectBuilder<UsageMetricsFilter>> fn) {
      return filter(FilterBuilders.usageMetrics(fn));
    }

    @Override
    public UsageMetricsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      return new UsageMetricsQuery(filter);
    }
  }
}
