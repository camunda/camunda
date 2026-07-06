/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import io.camunda.analytics.dataset.FilterPredicate;
import java.util.List;

/**
 * A read request against a cube, in domain terms: which {@code meters} to compute, grouped by
 * {@code groupBy} (a subset of the cube's grain — dropped dimensions are rolled up), over {@code
 * [fromMs, toMs)} bucketed at {@code granularityMs} (a single bucket &ge; the range yields one
 * total row; a granularity equal to a tier yields a per-window series), with extra {@code filters}.
 * The minimal neutral model this iteration; it grows in Phase 5 (ordering, paging, richer
 * predicates).
 *
 * @param groupBy dimension names to group by; empty means the grand total over all grain values
 * @param granularityMs the output time-bucket width; the planner reads the tier that divides it
 */
public record ReportQuery(
    List<String> groupBy,
    long fromMs,
    long toMs,
    long granularityMs,
    List<FilterPredicate> filters,
    List<String> meters) {

  public ReportQuery {
    groupBy = List.copyOf(groupBy);
    filters = List.copyOf(filters == null ? List.of() : filters);
    meters = List.copyOf(meters);
    if (meters.isEmpty()) {
      throw new IllegalArgumentException("report query requests no meters");
    }
    if (granularityMs <= 0) {
      throw new IllegalArgumentException("granularity must be positive, was " + granularityMs);
    }
    if (toMs < fromMs) {
      throw new IllegalArgumentException("empty time range [" + fromMs + ", " + toMs + ")");
    }
  }
}
