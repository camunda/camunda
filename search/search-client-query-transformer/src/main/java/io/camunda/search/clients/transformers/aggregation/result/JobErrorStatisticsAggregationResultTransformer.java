/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_BY_ERROR;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_SOURCE_NAME_ERROR_CODE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_SOURCE_NAME_ERROR_MESSAGE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_WORKERS;

import io.camunda.search.aggregation.result.JobErrorStatisticsAggregationResult;
import io.camunda.search.clients.aggregator.SearchCompositeAggregator;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.JobErrorStatisticsEntity;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JobErrorStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<JobErrorStatisticsAggregationResult> {

  @Override
  public JobErrorStatisticsAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {

    final var byErrorAgg = aggregations.get(AGGREGATION_BY_ERROR);
    if (byErrorAgg == null || byErrorAgg.aggregations() == null) {
      return new JobErrorStatisticsAggregationResult(Collections.emptyList(), null);
    }

    final var perErrorBuckets = byErrorAgg.aggregations();

    final List<JobErrorStatisticsEntity> items =
        perErrorBuckets.entrySet().stream()
            .map(
                entry -> {
                  final String compositeKey = entry.getKey();
                  final var agg = entry.getValue();
                  if (agg == null) {
                    return null;
                  }

                  // Composite key is sorted alphabetically by source name, so errorCode is always
                  // first and errorMessage second (if present)
                  final var parts =
                      SearchCompositeAggregator.splitKeyValues(
                          compositeKey,
                          AGGREGATION_SOURCE_NAME_ERROR_CODE,
                          AGGREGATION_SOURCE_NAME_ERROR_MESSAGE);
                  final String errorCode = parts.get(AGGREGATION_SOURCE_NAME_ERROR_CODE);
                  final String errorMessage = parts.get(AGGREGATION_SOURCE_NAME_ERROR_MESSAGE);

                  final int workers = extractCardinality(agg.aggregations());

                  return new JobErrorStatisticsEntity(errorCode, errorMessage, workers);
                })
            .filter(Objects::nonNull)
            .toList();

    return new JobErrorStatisticsAggregationResult(items, byErrorAgg.endCursor());
  }

  private int extractCardinality(final Map<String, AggregationResult> aggregations) {
    if (aggregations == null) {
      return 0;
    }
    final var agg = aggregations.get(AGGREGATION_WORKERS);
    if (agg != null && agg.docCount() != null) {
      return agg.docCount().intValue();
    }
    return 0;
  }
}
