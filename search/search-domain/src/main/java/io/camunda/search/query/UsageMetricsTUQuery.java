/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.UsageMetricsTUAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UsageMetricsTUFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record UsageMetricsTUQuery(UsageMetricsTUFilter filter)
    implements TypedSearchAggregationQuery<UsageMetricsTUFilter, UsageMetricsTUAggregation> {

  public static UsageMetricsTUQuery of(
      final Function<Builder, ObjectBuilder<UsageMetricsTUQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public UsageMetricsTUAggregation aggregation() {
    return new UsageMetricsTUAggregation(filter);
  }

  public static final class Builder implements ObjectBuilder<UsageMetricsTUQuery> {
    private static final UsageMetricsTUFilter EMPTY_FILTER =
        FilterBuilders.usageMetricsTU().build();

    private UsageMetricsTUFilter filter;

    public Builder filter(final UsageMetricsTUFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<UsageMetricsTUFilter.Builder, ObjectBuilder<UsageMetricsTUFilter>> fn) {
      return filter(FilterBuilders.usageMetricsTU(fn));
    }

    @Override
    public UsageMetricsTUQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      return new UsageMetricsTUQuery(filter);
    }
  }
}
