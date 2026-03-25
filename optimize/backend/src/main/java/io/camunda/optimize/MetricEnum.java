/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

public enum MetricEnum {
  OVERALL_IMPORT_TIME_METRIC(
      MetricType.IMPORT,
      "overallImportTime",
      "Records the time between the timestamp of a Zeebe record and the time of successful import to Optimize"),
  INDEXING_DURATION_METRIC(
      MetricType.IMPORT,
      "indexingDuration",
      "Records the time spent indexing data from Zeebe into Optimize Elasticsearch indexes"),
  NEW_PAGE_FETCH_TIME_METRIC(
      MetricType.IMPORT,
      "newPageFetchTime",
      "Records the time spent for fetching next import page from Zeebe Elasticsearch"),
  REPORT_LATENCY_METRIC(
      MetricType.REPORT, "reportLatency", "Records the time taken to evaluate a report"),
  ERROR_METRIC(MetricType.GENERAL, "error", "Counter for errors occurred across Optimize");
  private final String id;
  private final String name;
  private final String description;

  MetricEnum(final MetricType metricType, final String id, final String description) {
    this.id = id;
    this.description = description;
    name = metricType.prefix + "." + id;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  private enum MetricType {
    IMPORT("optimize.import"),
    REPORT("optimize.report"),
    GENERAL("optimize");
    private final String prefix;

    MetricType(final String prefix) {
      this.prefix = prefix;
    }
  }
}
