/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import java.util.Map;

/**
 * One finalized result row of a report: the group-by {@code dimensions} (a {@code null} value is
 * the "unknown" bucket), the output time bucket {@code windowStart}, and the read-facing {@code
 * measures} per meter (e.g. a {@code Long} count, a {@code RatioResult}, an {@code
 * ExecutionTimeSummaryResult}) — already merged across cells and finalized by the meter.
 */
public record ReportRow(
    Map<String, Object> dimensions, long windowStart, Map<String, Object> measures) {

  public ReportRow {
    dimensions = Map.copyOf(dimensions);
    measures = Map.copyOf(measures);
  }
}
