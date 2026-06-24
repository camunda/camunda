/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_AFFECTED_INSTANCES;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_BY_ERROR;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_ERROR_HASH;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_TOTAL_ESTIMATE;

import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByErrorAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class IncidentProcessInstanceStatisticsByErrorAggregationResultTransformer
    implements AggregationResultTransformer<
        IncidentProcessInstanceStatisticsByErrorAggregationResult> {

  public static final String NULL = "__NULL__";

  @Override
  public IncidentProcessInstanceStatisticsByErrorAggregationResult apply(
      final Map<String, AggregationResult> value) {

    final AggregationResult byErrorAgg = value.get(AGGREGATION_NAME_BY_ERROR);
    final Map<String, AggregationResult> perErrorAggs =
        byErrorAgg != null ? byErrorAgg.aggregations() : Map.of();

    final List<IncidentProcessInstanceStatisticsByErrorEntity> items = new ArrayList<>();

    for (Map.Entry<String, AggregationResult> entry : perErrorAggs.entrySet()) {
      final String bucketKey = entry.getKey();
      final AggregationResult bucketAgg = entry.getValue();
      final Map<String, AggregationResult> subAggs =
          bucketAgg != null ? bucketAgg.aggregations() : Map.of();

      final String errorMessage = NULL.equals(bucketKey) ? null : bucketKey;

      final AggregationResult errorHashAgg =
          subAggs.getOrDefault(AGGREGATION_NAME_ERROR_HASH, AggregationResult.EMPTY);
      final Map<String, AggregationResult> hashBuckets = errorHashAgg.aggregations();

      if (hashBuckets == null || hashBuckets.isEmpty()) {
        throw new IllegalStateException(
            "Missing required error hash aggregation for bucket key: " + bucketKey);
      }

      final Integer parsedErrorHashKey =
          hashBuckets.keySet().stream()
              .filter(Objects::nonNull)
              .map(
                  IncidentProcessInstanceStatisticsByErrorAggregationResultTransformer::tryParseInt)
              .findFirst()
              .orElse(null);

      final int errorHash = resolveErrorHashCode(errorMessage, parsedErrorHashKey);

      final long activeInstancesWithErrorCount =
          subAggs
              .getOrDefault(AGGREGATION_NAME_AFFECTED_INSTANCES, AggregationResult.EMPTY)
              .docCount();

      items.add(
          new IncidentProcessInstanceStatisticsByErrorEntity(
              errorHash, errorMessage, activeInstancesWithErrorCount));
    }

    final AggregationResult cardinalityAgg = value.get(AGGREGATION_NAME_TOTAL_ESTIMATE);
    final int totalItems =
        cardinalityAgg != null ? Math.toIntExact(cardinalityAgg.docCount()) : perErrorAggs.size();

    return new IncidentProcessInstanceStatisticsByErrorAggregationResult(items, totalItems);
  }

  private static int tryParseInt(final String value) {
    try {
      return Integer.parseInt(value);
    } catch (final NumberFormatException e) {
      throw new IllegalStateException(
          "Failed to parse error hash bucket key to integer: " + value, e);
    }
  }

  private static int resolveErrorHashCode(
      final String errorMessageBucketKey, final Integer errorHashBucketKey) {
    if (errorHashBucketKey != null) {
      return errorHashBucketKey;
    }

    if (errorMessageBucketKey != null) {
      return errorMessageBucketKey.hashCode();
    }

    throw new IllegalStateException(
        "Failed to resolve error hash code: both errorMessageBucketKey and errorHashBucketKey are null");
  }
}
