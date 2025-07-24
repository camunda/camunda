/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import io.camunda.search.aggregation.UsageMetricsAggregation;
import io.camunda.search.aggregation.result.UsageMetricsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant.Builder;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class UsageMetricsAggregationResultTransformer
    implements AggregationResultTransformer<UsageMetricsAggregationResult> {

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
  public UsageMetricsAggregationResult apply(final Map<String, AggregationResult> aggregations) {

    final boolean withTenants =
        !aggregations.containsKey(UsageMetricsAggregation.AGGREGATION_TERMS_EVENT_TYPE);
    final UsageMetricStatisticsEntity res;

    if (withTenants) {
      final var tenantsAgg = aggregations.get(UsageMetricsAggregation.AGGREGATION_TERMS_TENANT_ID);

      long totalRpi = 0;
      long totalEdi = 0;
      final var tenants =
          new HashMap<String, UsageMetricStatisticsEntityTenant>(tenantsAgg.aggregations().size());

      for (final Entry<String, AggregationResult> entry : tenantsAgg.aggregations().entrySet()) {
        final String tenantId = entry.getKey();
        final AggregationResult tenantIdAgg = entry.getValue();
        final var eventTypesAgg =
            tenantIdAgg.aggregations().get(UsageMetricsAggregation.AGGREGATION_TERMS_EVENT_TYPE);

        final long tenantRpi = extractMetricCount(eventTypesAgg, UsageMetricsEventType.RPI);
        final long tenantEdi = extractMetricCount(eventTypesAgg, UsageMetricsEventType.EDI);

        totalRpi += tenantRpi;
        totalEdi += tenantEdi;

        tenants.put(tenantId, new Builder().rpi(tenantRpi).edi(tenantEdi).build());
      }

      res = new UsageMetricStatisticsEntity(totalRpi, totalEdi, tenants.size(), tenants);
    } else {
      final var eventTypesAgg =
          aggregations.get(UsageMetricsAggregation.AGGREGATION_TERMS_EVENT_TYPE);
      final var totalRpi = extractMetricCount(eventTypesAgg, UsageMetricsEventType.RPI);
      final var totalEdi = extractMetricCount(eventTypesAgg, UsageMetricsEventType.EDI);

      final var tenantsAgg = aggregations.get(UsageMetricsAggregation.AGGREGATION_TERMS_TENANT_ID);
      final var totalAt = tenantsAgg.aggregations().size();

      res = new UsageMetricStatisticsEntity(totalRpi, totalEdi, totalAt, null);
    }

    return new UsageMetricsAggregationResult(res);
  }
}
