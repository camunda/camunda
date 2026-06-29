/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A partition's disk-space health, as an explicit leaf of the partition's health tree. Starts
 * healthy (disk space is assumed available until a disk-usage monitor says otherwise) and becomes
 * unhealthy while disk space is exhausted.
 */
final class DiskSpaceHealth implements HealthMonitorable {

  static final String COMPONENT_NAME = "DiskSpace";

  // The listeners are only ever touched on the partition actor. The report is also read by the
  // metric projection on the scrape thread, so it is published through a volatile field.
  private final Set<FailureListener> failureListeners = new HashSet<>();

  // We assume disk space is available until otherwise notified.
  @SuppressWarnings("java:S3077") // volatile is fine, HealthReport is immutable
  private volatile HealthReport healthReport = HealthReport.healthy(this);

  @Override
  public String componentName() {
    return COMPONENT_NAME;
  }

  @Override
  public HealthReport getHealthReport() {
    return healthReport;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    failureListeners.add(failureListener);
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    failureListeners.remove(failureListener);
  }

  void setDiskSpaceAvailable(final boolean diskSpaceAvailable) {
    final var previous = healthReport;
    healthReport =
        diskSpaceAvailable
            ? HealthReport.healthy(this)
            : HealthReport.unhealthy(this)
                .withMessage("Not enough disk space available", Instant.now());
    if (!Objects.equals(previous, healthReport)) {
      failureListeners.forEach(l -> l.onHealthReport(healthReport));
    }
  }
}
