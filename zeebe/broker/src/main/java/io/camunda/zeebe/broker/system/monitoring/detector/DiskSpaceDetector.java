/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import java.time.InstantSource;

/**
 * Shows when the disk space monitor finds free space below its configured minimum, which pauses
 * processing. The thresholds are the disk monitor's own.
 */
public final class DiskSpaceDetector extends HealthDetector implements DiskSpaceUsageListener {

  private static final String CHILD = "DataDirectory";

  private final InstantSource clock;

  public DiskSpaceDetector(final InstantSource clock) {
    super("DiskSpace");
    this.clock = clock;
    markHealthy(CHILD);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    markDegraded(
        CHILD,
        "free disk space is below the configured minimum; processing and replication pause",
        clock.instant());
  }

  @Override
  public void onDiskSpaceAvailable() {
    markHealthy(CHILD);
  }
}
