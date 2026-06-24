/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.UsageMetricsTUFilter;

public class UsageMetricsQueryMapper {

  public static UsageMetricsTUQuery mapToUsageMetricsTUQuery(final UsageMetricsQuery query) {
    return new UsageMetricsTUQuery.Builder()
        .filter(
            new UsageMetricsTUFilter.Builder()
                .startTime(query.filter().startTime())
                .endTime(query.filter().endTime())
                .tenantId(query.filter().tenantId())
                .withTenants(query.filter().withTenants())
                .build())
        .build();
  }
}
