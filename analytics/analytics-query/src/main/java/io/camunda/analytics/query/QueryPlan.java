/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.serving.spi.AggregatedFetch;
import io.camunda.analytics.serving.spi.DatasetFetch;
import java.util.List;

/**
 * What the {@link DatasetQueryPlanner} decided for a {@link ReportQuery}: the per-meter read
 * strategy split into {@code streamFetches} (sketch/blob meters the executor streams and
 * app-merges, one per tier) and {@code aggregatedFetches} (additive meters the store reduces, one
 * per {@code (tier, strategy)}), plus the {@code groupBy}/{@code granularityMs} the executor
 * buckets by and the requested {@code meters}. The split of work between backend and application is
 * captured here so the executor is backend-agnostic; the executor unions both kinds on {@code
 * (group, bucket)}.
 */
public record QueryPlan(
    CompiledDataset dataset,
    List<DatasetFetch> streamFetches,
    List<AggregatedFetch> aggregatedFetches,
    List<String> groupBy,
    long granularityMs,
    List<String> meters) {

  public QueryPlan {
    streamFetches = List.copyOf(streamFetches);
    aggregatedFetches = List.copyOf(aggregatedFetches);
    groupBy = List.copyOf(groupBy);
    meters = List.copyOf(meters);
  }
}
