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
      "overallImportTime",
      "Records the time between the timestamp of a Zeebe record and the time of successful import to Optimize"),
  INDEXING_DURATION_METRIC(
      "indexingDuration",
      "Records the time spent indexing data from Zeebe into Optimize Elasticsearch indexes"),
  NEW_PAGE_FETCH_TIME_METRIC(
      "newPageFetchTime",
      "Records the time spent for fetching next import page from Zeebe Elasticsearch");
  private static final String IMPORT_METRICS_PREFIX = "optimize.import";
  private final String id;
  private final String name;
  private final String description;

  MetricEnum(final String id, final String description) {
    this.id = id;
    this.description = description;
    name = IMPORT_METRICS_PREFIX + "." + id;
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
}
