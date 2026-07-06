/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.report;

import io.camunda.analytics.dataset.FilterPredicate;
import java.util.List;

/**
 * One dataset a {@link ReportDefinition} draws from: the dataset's {@code name}, which of its
 * {@code meters} to read, and dataset-local {@code filters}. The report's shared group-by, time
 * range, and granularity are applied per source; the resulting measures are namespaced by {@link
 * #datasetName} in the combined result so two sources exposing a same-named meter (e.g. {@code
 * count}) don't collide.
 *
 * @param datasetName the referenced dataset's name (resolved to a compiled cube at execution)
 * @param meters the meters to read from that dataset (non-empty)
 * @param filters dataset-local equality filters, or empty
 */
public record ReportSource(String datasetName, List<String> meters, List<FilterPredicate> filters) {

  public ReportSource {
    if (datasetName == null || datasetName.isBlank()) {
      throw new IllegalArgumentException("report source requires a dataset name");
    }
    meters = List.copyOf(meters);
    filters = List.copyOf(filters == null ? List.of() : filters);
    if (meters.isEmpty()) {
      throw new IllegalArgumentException("report source '" + datasetName + "' selects no meters");
    }
  }
}
