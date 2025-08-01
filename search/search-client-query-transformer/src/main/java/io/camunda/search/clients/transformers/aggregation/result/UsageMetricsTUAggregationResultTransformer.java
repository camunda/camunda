/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.UsageMetricsTUAggregation.AGGREGATION_TERMS_ASSIGNEE_HASH;
import static io.camunda.search.aggregation.UsageMetricsTUAggregation.AGGREGATION_TERMS_TENANT_ID;

import io.camunda.search.aggregation.result.UsageMetricsTUAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant.Builder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class UsageMetricsTUAggregationResultTransformer
    implements AggregationResultTransformer<UsageMetricsTUAggregationResult> {

  @Override
  public UsageMetricsTUAggregationResult apply(final Map<String, AggregationResult> aggregations) {

    final boolean withTenants = aggregations.containsKey(AGGREGATION_TERMS_TENANT_ID);
    final UsageMetricTUStatisticsEntity res;

    final var totalTu = aggregations.get(AGGREGATION_TERMS_ASSIGNEE_HASH).docCount();

    if (withTenants) {
      final var tenantsAgg = aggregations.get(AGGREGATION_TERMS_TENANT_ID);

      final var tenants =
          new HashMap<String, UsageMetricTUStatisticsEntityTenant>(
              tenantsAgg.aggregations().size());
      for (final Entry<String, AggregationResult> entry : tenantsAgg.aggregations().entrySet()) {
        final String tenantId = entry.getKey();
        final AggregationResult tenantIdAgg = entry.getValue();

        final long tenantTu =
            tenantIdAgg.aggregations().get(AGGREGATION_TERMS_ASSIGNEE_HASH).docCount();
        tenants.put(tenantId, new Builder().tu(tenantTu).build());
      }
      res = new UsageMetricTUStatisticsEntity(totalTu, tenants);
    } else {
      res = new UsageMetricTUStatisticsEntity(totalTu, null);
    }

    return new UsageMetricsTUAggregationResult(res);
  }
}
