/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import lombok.Getter;

public enum MetricEnum {
  OVERALL_IMPORT_TIME_METRIC(
    "overallImportTime",
    "Records the time between the timestamp of a Zeebe record and the time of successful import to Optimize"
  ),
  INDEXING_DURATION_METRIC(
    "indexingDuration",
    "Records the time spent indexing data from Zeebe into Optimize Elasticsearch indexes"
  ),
  NEW_PAGE_FETCH_TIME_METRIC(
    "newPageFetchTime",
    "Records the time spent for fetching next import page from Zeebe Elasticsearch"
  );
  private static final String IMPORT_METRICS = "import";
  @Getter
  private final String id;
  @Getter
  private final String name;
  @Getter
  private final String description;

  MetricEnum(String id, String description) {
    this.id = id;
    this.description = description;
    this.name = IMPORT_METRICS + "." + id;
  }
}
