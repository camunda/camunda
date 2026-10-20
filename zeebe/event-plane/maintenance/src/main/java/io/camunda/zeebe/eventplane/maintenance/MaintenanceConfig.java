/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * What maintenance does.
 *
 * @param rollupAge how old a publisher's segment must be, by the upload time in its name, before it
 *     is rolled up
 * @param rollupTargetBytes the object size at which a rollup stops taking on segments; a rollup
 *     holds about this much, and is held in memory while it is written
 * @param retention how long a segment is kept, by its upload time; null keeps everything
 */
public record MaintenanceConfig(
    Duration rollupAge, long rollupTargetBytes, @Nullable Duration retention) {

  public static final Duration DEFAULT_ROLLUP_AGE = Duration.ofDays(1);
  public static final long DEFAULT_ROLLUP_TARGET_BYTES = 256L << 20;

  public MaintenanceConfig {
    if (rollupAge.isNegative()) {
      throw new InvalidMaintenanceConfigException("rollupAge", "to be 0 or more", "" + rollupAge);
    }
    if (rollupTargetBytes < 1) {
      throw new InvalidMaintenanceConfigException(
          "rollupTargetBytes", "to be at least 1", "" + rollupTargetBytes);
    }
    if (retention != null && (retention.isNegative() || retention.isZero())) {
      throw new InvalidMaintenanceConfigException(
          "retention", "to be longer than 0, or unset", "" + retention);
    }
  }

  /** Rolls up after a day into rollups of 256 MiB, and keeps everything. */
  public static MaintenanceConfig defaults() {
    return new MaintenanceConfig(DEFAULT_ROLLUP_AGE, DEFAULT_ROLLUP_TARGET_BYTES, null);
  }
}
