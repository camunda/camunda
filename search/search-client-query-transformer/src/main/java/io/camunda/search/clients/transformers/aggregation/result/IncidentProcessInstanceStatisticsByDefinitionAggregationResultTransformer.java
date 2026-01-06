/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.*;

import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByDefinitionAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncidentProcessInstanceStatisticsByDefinitionAggregationResultTransformer
    implements AggregationResultTransformer<
        IncidentProcessInstanceStatisticsByDefinitionAggregationResult> {

  @Override
  public IncidentProcessInstanceStatisticsByDefinitionAggregationResult apply(
      final Map<String, AggregationResult> value) {

    final AggregationResult byDefinitionAgg = value.get(AGGREGATION_NAME_BY_DEFINITION);
    final Map<String, AggregationResult> perDefinitionAggs =
        byDefinitionAgg != null ? byDefinitionAgg.aggregations() : Map.of();

    final List<IncidentProcessInstanceStatisticsByDefinitionEntity> items = new ArrayList<>();

    for (final Map.Entry<String, AggregationResult> definitionEntry :
        perDefinitionAggs.entrySet()) {
      final long processDefinitionKey = parseProcessDefinitionKey(definitionEntry.getKey());
      final AggregationResult definitionBucket = definitionEntry.getValue();
      final Map<String, AggregationResult> tenantBuckets =
          definitionBucket != null && definitionBucket.aggregations() != null
              ? definitionBucket.aggregations()
              : Map.of();

      final AggregationResult tenantsAgg = tenantBuckets.get(AGGREGATION_NAME_BY_TENANT);
      final Map<String, AggregationResult> perTenantAggs =
          tenantsAgg != null && tenantsAgg.aggregations() != null
              ? tenantsAgg.aggregations()
              : Map.of();

      if (perTenantAggs.isEmpty()) {
        // No tenant sub-buckets: treat tenant as null
        final long activeInstancesWithErrorCount =
            tenantBuckets
                .getOrDefault(AGGREGATION_NAME_AFFECTED_INSTANCES, AggregationResult.EMPTY)
                .docCount();
        items.add(
            new IncidentProcessInstanceStatisticsByDefinitionEntity(
                null, processDefinitionKey, null, null, null, activeInstancesWithErrorCount));
      } else {
        for (final Map.Entry<String, AggregationResult> tenantEntry : perTenantAggs.entrySet()) {
          final String tenantId = tenantEntry.getKey();
          final AggregationResult tenantAgg = tenantEntry.getValue();
          final Map<String, AggregationResult> subAggs =
              tenantAgg != null && tenantAgg.aggregations() != null
                  ? tenantAgg.aggregations()
                  : Map.of();

          final long activeInstancesWithErrorCount =
              subAggs
                  .getOrDefault(AGGREGATION_NAME_AFFECTED_INSTANCES, AggregationResult.EMPTY)
                  .docCount();

          items.add(
              new IncidentProcessInstanceStatisticsByDefinitionEntity(
                  null, processDefinitionKey, null, null, tenantId, activeInstancesWithErrorCount));
        }
      }
    }

    final int totalItems = perDefinitionAggs.size();

    return new IncidentProcessInstanceStatisticsByDefinitionAggregationResult(items, totalItems);
  }

  private static long parseProcessDefinitionKey(final String bucketKey) {
    if (bucketKey == null || bucketKey.isBlank()) {
      throw new IllegalStateException("Missing required bucket key for by-definition aggregation");
    }

    try {
      return Long.parseLong(bucketKey);
    } catch (final NumberFormatException e) {
      throw new IllegalStateException(
          "Invalid processDefinitionKey in bucket key for by-definition aggregation: '"
              + bucketKey
              + "'",
          e);
    }
  }
}
