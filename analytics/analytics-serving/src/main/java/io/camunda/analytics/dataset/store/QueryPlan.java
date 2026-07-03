/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.CompiledDataset;
import java.util.List;

/**
 * What the {@link DatasetQueryPlanner} decided for a {@link ReportQuery}: the backend {@code
 * fetches} (one per tier — what to pull), the {@code groupBy} and {@code granularityMs} the {@link
 * DatasetQueryExecutor} applies in the application (what to merge), and the requested {@code
 * meters}. The split of work between backend and application is captured here so the executor is
 * backend-agnostic.
 */
public record QueryPlan(
    CompiledDataset dataset,
    List<DatasetFetch> fetches,
    List<String> groupBy,
    long granularityMs,
    List<String> meters) {

  public QueryPlan {
    fetches = List.copyOf(fetches);
    groupBy = List.copyOf(groupBy);
    meters = List.copyOf(meters);
  }
}
