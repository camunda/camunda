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

  private static final String BUCKET_KEY_DELIMITER = "::";

  @Override
  public IncidentProcessInstanceStatisticsByDefinitionAggregationResult apply(
      final Map<String, AggregationResult> value) {

    final AggregationResult byDefinitionAgg = value.get(AGGREGATION_NAME_BY_DEFINITION);
    final Map<String, AggregationResult> perDefinitionAggs =
        byDefinitionAgg != null ? byDefinitionAgg.aggregations() : Map.of();

    final List<IncidentProcessInstanceStatisticsByDefinitionEntity> items = new ArrayList<>();

    for (final Map.Entry<String, AggregationResult> entry : perDefinitionAggs.entrySet()) {
      final String bucketKey = entry.getKey();
      final AggregationResult bucketAgg = entry.getValue();
      final Map<String, AggregationResult> subAggs =
          bucketAgg != null ? bucketAgg.aggregations() : Map.of();

      final BucketKey parsedKey = BucketKey.parse(bucketKey);

      final long activeInstancesWithErrorCount =
          subAggs
              .getOrDefault(AGGREGATION_NAME_AFFECTED_INSTANCES, AggregationResult.EMPTY)
              .docCount();

      items.add(
          new IncidentProcessInstanceStatisticsByDefinitionEntity(
              null,
              parsedKey.processDefinitionKey(),
              null,
              null,
              parsedKey.tenantId(),
              activeInstancesWithErrorCount));
    }

    final AggregationResult cardinalityAgg = value.get(AGGREGATION_NAME_TOTAL_ESTIMATE);
    final int totalItems =
        cardinalityAgg != null
            ? Math.toIntExact(cardinalityAgg.docCount())
            : perDefinitionAggs.size();

    return new IncidentProcessInstanceStatisticsByDefinitionAggregationResult(items, totalItems);
  }

  private record BucketKey(long processDefinitionKey, String tenantId) {
    private static BucketKey parse(final String bucketKey) {
      if (bucketKey == null || bucketKey.isBlank()) {
        throw new IllegalStateException(
            "Missing required bucket key for by-definition aggregation");
      }

      final String[] parts = bucketKey.split(BUCKET_KEY_DELIMITER, -1);
      if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
        throw new IllegalStateException(
            "Invalid bucket key for by-definition aggregation: '" + bucketKey + "'");
      }

      try {
        return new BucketKey(Long.parseLong(parts[0]), parts[1]);
      } catch (final NumberFormatException e) {
        throw new IllegalStateException(
            "Invalid processDefinitionKey in bucket key for by-definition aggregation: '"
                + bucketKey
                + "'",
            e);
      }
    }
  }
}
