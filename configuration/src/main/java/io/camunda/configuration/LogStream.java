/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;
import org.springframework.util.unit.DataSize;

public class LogStream {
  private static final String PREFIX = "camunda.data.primary-storage.log-stream";

  private static final Set<String> LEGACY_INDEX_DENSITY_PROPERTIES =
      Set.of("zeebe.broker.data.logIndexDensity");
  private static final Set<String> LEGACY_SEGMENT_SIZE_PROPERTIES =
      Set.of("zeebe.broker.data.logSegmentSize");

  /**
   * The density of the log index, which determines how frequently index entries are created in the
   * log. This value specifies the number of log entries between each index entry. A lower value
   * increases the number of index entries (improving lookup speed but using more memory), while a
   * higher value reduces the number of index entries (saving memory but potentially slowing
   * lookups).
   *
   * <p>Valid values: any positive integer (recommended range: 1-1000). Default: 100.
   */
  private int logIndexDensity = 100;

  /** The size of data log segment files. */
  private DataSize logSegmentSize = DataSize.ofMegabytes(128);

  public int getLogIndexDensity() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".log-index-density",
        logIndexDensity,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_INDEX_DENSITY_PROPERTIES);
  }

  public void setLogIndexDensity(final int logIndexDensity) {
    this.logIndexDensity = logIndexDensity;
  }

  public DataSize getLogSegmentSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".log-segment-size",
        logSegmentSize,
        DataSize.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SEGMENT_SIZE_PROPERTIES);
  }

  public void setLogSegmentSize(final DataSize logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }
}
