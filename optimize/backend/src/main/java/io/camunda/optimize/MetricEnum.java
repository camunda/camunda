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
  IMPORT_CYCLE_DURATION_METRIC(
      MetricType.IMPORT,
      "cycleDuration",
      "Records the total import cycle time (ES fetch + DB write) per mediator"),
  IMPORT_MEDIATOR_ERROR_METRIC(
      MetricType.IMPORT,
      "mediatorErrors",
      "Counts import errors per mediator, tagged with record type and partition"),
  NEW_PAGE_FETCH_TIME_METRIC(
      MetricType.IMPORT,
      "newPageFetchTime",
      "Records the time spent for fetching next import page from Zeebe Elasticsearch"),
  IMPORT_DB_WRITE_FAILURES_METRIC(
      MetricType.IMPORT,
      "dbWriteFailures",
      "Counts failed attempts to write an import page to the database, the page is retried until"
          + " it succeeds"),
  IMPORTED_UNTIL_METRIC(
      MetricType.IMPORT,
      "importedUntil",
      "Epoch time up to which all exported records of this type and partition are imported",
      "seconds"),
  FETCH_PAGE_SIZE_METRIC(
      MetricType.IMPORT,
      "fetchPageSize",
      "Page size used to fetch Zeebe records, reduced after failed fetches and gradually restored"),
  MAX_PAGE_SIZE_METRIC(
      MetricType.IMPORT, "maxPageSize", "Configured maximum page size of Zeebe record fetches"),
  CONFIGURED_PARTITIONS_METRIC(
      MetricType.IMPORT,
      "configuredPartitions",
      "Number of Zeebe partitions Optimize is configured to import from"),
  ZEEBE_INDEX_MISSING_METRIC(
      MetricType.IMPORT,
      "zeebeIndexMissing",
      "Counts fetches that found no Zeebe record index to read from"),
  REPORT_LATENCY_METRIC(
      MetricType.REPORT, "reportLatency", "Records the time taken to evaluate a report"),
  ERROR_METRIC(MetricType.GENERAL, "error", "Counter for errors occurring across Optimize");
  private final String id;
  private final String name;
  private final String description;
  private final String baseUnit;

  MetricEnum(final MetricType metricType, final String id, final String description) {
    this(metricType, id, description, null);
  }

  MetricEnum(
      final MetricType metricType,
      final String id,
      final String description,
      final String baseUnit) {
    this.id = id;
    this.description = description;
    this.baseUnit = baseUnit;
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

  public String getBaseUnit() {
    return baseUnit;
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
