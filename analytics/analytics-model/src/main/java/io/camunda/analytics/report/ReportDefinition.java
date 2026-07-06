/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.report;

import java.util.List;

/**
 * A saved report that reads one or more datasets and presents them together — a logical view over
 * the pre-aggregated cubes (ADR 0006), not new storage. It fixes the shared shape (the {@code
 * groupBy} dimensions every source is grouped by, the output {@code granularityMs}, and how the
 * sources {@link Combination combine}); the concrete time range is a view-time parameter, so it is
 * not part of the saved definition. Each {@link ReportSource} contributes its dataset's meters,
 * namespaced by dataset in the result.
 *
 * @param reportId the stable id assigned at creation
 * @param name the report's display name
 * @param sources the datasets this report reads (non-empty)
 * @param groupBy the shared group-by dimensions (a subset of every source's grain); empty means the
 *     grand total
 * @param granularityMs the output time-bucket width (positive)
 * @param combination how the sources are combined (UNION today)
 * @param viz UI hint for how to render the report (opaque to the executor), or {@code null}
 */
public record ReportDefinition(
    long reportId,
    String name,
    List<ReportSource> sources,
    List<String> groupBy,
    long granularityMs,
    Combination combination,
    String viz) {

  public ReportDefinition {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("report requires a name");
    }
    sources = List.copyOf(sources);
    groupBy = List.copyOf(groupBy);
    if (sources.isEmpty()) {
      throw new IllegalArgumentException("report '" + name + "' references no datasets");
    }
    if (combination == null) {
      throw new IllegalArgumentException("report '" + name + "' has no combination");
    }
    if (granularityMs <= 0) {
      throw new IllegalArgumentException("granularity must be positive, was " + granularityMs);
    }
  }
}
