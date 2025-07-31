/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import io.camunda.search.aggregation.UsageMetricsAggregation;
import io.camunda.search.aggregation.result.UsageMetricsTUAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant.Builder;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class UsageMetricsTUAggregationResultTransformer
    implements AggregationResultTransformer<UsageMetricsTUAggregationResult> {

  private long extractMetricCount(
      final AggregationResult eventTypes, final UsageMetricsEventType metricName) {
    final var metric = eventTypes.aggregations().get(metricName.name());
    if (metric != null) {
      return metric
          .aggregations()
          .get(UsageMetricsAggregation.AGGREGATION_SUM_EVENT_TYPE)
          .docCount();
    }
    return 0;
  }

  @Override
  public UsageMetricsTUAggregationResult apply(final Map<String, AggregationResult> aggregations) {

    final boolean withTenants =
        !aggregations.containsKey(UsageMetricsAggregation.AGGREGATION_TERMS_EVENT_TYPE);
    final UsageMetricTUStatisticsEntity res;

    if (withTenants) {
      final var tenantsAgg = aggregations.get(UsageMetricsAggregation.AGGREGATION_TERMS_TENANT_ID);

      long totalTu = 0;
      final var tenants =
          new HashMap<String, UsageMetricTUStatisticsEntityTenant>(
              tenantsAgg.aggregations().size());
      for (final Entry<String, AggregationResult> entry : tenantsAgg.aggregations().entrySet()) {
        final String tenantId = entry.getKey();
        final AggregationResult tenantIdSet = entry.getValue();

        final var eventTypesAgg =
            tenantIdSet.aggregations().get(UsageMetricsAggregation.AGGREGATION_TERMS_EVENT_TYPE);
        final long tenantTu = extractMetricCount(eventTypesAgg, UsageMetricsEventType.TU);
        totalTu += tenantTu;
        tenants.put(tenantId, new Builder().tu(tenantTu).build());
      }
      res = new UsageMetricTUStatisticsEntity(totalTu, tenants);
    } else {
      final var eventTypesAgg =
          aggregations.get(UsageMetricsAggregation.AGGREGATION_TERMS_EVENT_TYPE);
      final long totalTu = extractMetricCount(eventTypesAgg, UsageMetricsEventType.TU);

      res = new UsageMetricTUStatisticsEntity(totalTu, null);
    }

    return new UsageMetricsTUAggregationResult(res);
  }
}
