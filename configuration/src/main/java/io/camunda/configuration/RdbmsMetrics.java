/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

/** Configuration for RDBMS metrics. */
public class RdbmsMetrics {

  /** Default cache duration for table row count metrics. */
  public static final Duration DEFAULT_TABLE_ROW_COUNT_CACHE_DURATION = Duration.ofMinutes(5);

  /**
   * The duration for which the table row count metrics are cached before being refreshed from the
   * database. This helps avoid performance impact on the database.
   */
  private Duration tableRowCountCacheDuration = DEFAULT_TABLE_ROW_COUNT_CACHE_DURATION;

  public Duration getTableRowCountCacheDuration() {
    return tableRowCountCacheDuration;
  }

  public void setTableRowCountCacheDuration(final Duration tableRowCountCacheDuration) {
    this.tableRowCountCacheDuration = tableRowCountCacheDuration;
  }
}
