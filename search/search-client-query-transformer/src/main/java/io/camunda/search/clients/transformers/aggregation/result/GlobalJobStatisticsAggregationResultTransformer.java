/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_INCOMPLETE;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;

import io.camunda.search.aggregation.result.GlobalJobStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public class GlobalJobStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<GlobalJobStatisticsAggregationResult> {

  @Override
  public GlobalJobStatisticsAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {

    // Extract metrics from nested filter bucket aggregations
    final var createdMetric = extractStatusMetric(aggregations, AGGREGATION_CREATED);
    final var completedMetric = extractStatusMetric(aggregations, AGGREGATION_COMPLETED);
    final var failedMetric = extractStatusMetric(aggregations, AGGREGATION_FAILED);

    // Extract incomplete flag
    final boolean isIncomplete = extractMaxBoolean(aggregations);

    final var entity =
        new GlobalJobStatisticsEntity(createdMetric, completedMetric, failedMetric, isIncomplete);

    return new GlobalJobStatisticsAggregationResult(entity);
  }

  private StatusMetric extractStatusMetric(
      final Map<String, AggregationResult> aggregations, final String bucketName) {
    final var bucketAgg = aggregations.get(bucketName);
    if (bucketAgg == null || bucketAgg.aggregations() == null) {
      return new StatusMetric(0L, null);
    }

    final var subAggregations = bucketAgg.aggregations();
    final long count = extractDocCount(subAggregations);
    final OffsetDateTime lastUpdatedAt = extractMaxTimestamp(subAggregations);

    return new StatusMetric(count, lastUpdatedAt);
  }

  private long extractDocCount(final Map<String, AggregationResult> aggregations) {
    final var agg = aggregations.get(AGGREGATION_COUNT);
    if (agg != null && agg.docCount() != null) {
      return agg.docCount();
    }
    return 0L;
  }

  private OffsetDateTime extractMaxTimestamp(final Map<String, AggregationResult> aggregations) {
    final var agg = aggregations.get(AGGREGATION_LAST_UPDATED_AT);
    if (agg != null && agg.docCount() != null) {
      final long epochMillis = agg.docCount();
      if (epochMillis > 0) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
      }
    }
    return null;
  }

  private boolean extractMaxBoolean(final Map<String, AggregationResult> aggregations) {
    final var agg = aggregations.get(AGGREGATION_INCOMPLETE);
    if (agg != null && agg.docCount() != null) {
      // Max of boolean field: 0 = all false, 1 = at least one true
      return agg.docCount() > 0;
    }
    return false;
  }
}
