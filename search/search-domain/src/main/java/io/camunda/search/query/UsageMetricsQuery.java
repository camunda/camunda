/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UsageMetricsFilter;
import java.util.Objects;

public record UsageMetricsQuery(UsageMetricsFilter filter)
    implements TypedSearchAggregationQuery<UsageMetricsFilter> {

  public static final String AGGREGATION_PROCESS_INSTANCE = "PROCESS_INSTANCE";
  public static final String AGGREGATION_DECISION_INSTANCE = "DECISION_INSTANCE";
  public static final String AGGREGATION_USER_TASK_ASSIGNEES = "USER_TASK_ASSIGNEES";

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<UsageMetricsQuery.Builder> {
    private static final UsageMetricsFilter EMPTY_FILTER = FilterBuilders.usageMetrics().build();
    private UsageMetricsFilter filter;

    public UsageMetricsQuery.Builder filter(final UsageMetricsFilter value) {
      filter = value;
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    public UsageMetricsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      return new UsageMetricsQuery(filter);
    }
  }
}
