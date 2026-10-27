/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_BY_TYPE;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_WORKERS;

import io.camunda.search.aggregation.result.JobTypeStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JobTypeStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<JobTypeStatisticsAggregationResult> {

  @Override
  public JobTypeStatisticsAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {

    final var byTypeAgg = aggregations.get(AGGREGATION_BY_TYPE);
    if (byTypeAgg == null || byTypeAgg.aggregations() == null) {
      return new JobTypeStatisticsAggregationResult(Collections.emptyList(), null);
    }

    final var perTypeBuckets = byTypeAgg.aggregations();

    final List<JobTypeStatisticsEntity> items =
        perTypeBuckets.entrySet().stream()
            .map(
                entry -> {
                  final String jobType = entry.getKey();
                  final var agg = entry.getValue();
                  if (agg == null || agg.aggregations() == null || agg.aggregations().isEmpty()) {
                    return null;
                  }

                  final var subAggregations = agg.aggregations();

                  // Extract metrics from nested filter bucket aggregations
                  final var createdMetric =
                      extractStatusMetric(subAggregations, AGGREGATION_CREATED);
                  final var completedMetric =
                      extractStatusMetric(subAggregations, AGGREGATION_COMPLETED);
                  final var failedMetric = extractStatusMetric(subAggregations, AGGREGATION_FAILED);

                  // Extract workers count
                  final int workers = extractWorkersCount(subAggregations);

                  return new JobTypeStatisticsEntity(
                      jobType, createdMetric, completedMetric, failedMetric, workers);
                })
            .filter(Objects::nonNull)
            .toList();

    return new JobTypeStatisticsAggregationResult(items, byTypeAgg.endCursor());
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

  private int extractWorkersCount(final Map<String, AggregationResult> aggregations) {
    final var agg = aggregations.get(AGGREGATION_WORKERS);
    if (agg != null && agg.docCount() != null) {
      return Math.toIntExact(agg.docCount());
    }
    return 0;
  }
}
