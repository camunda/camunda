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
 * Reflects the health of ZeebePartition. The health is updated by ZeebePartition when role
 * transitions either succeeded or failed.
 */
class ZeebePartitionHealth implements HealthMonitorable {

  private final String name;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private HealthReport healthReport;
  private final PartitionTransition partitionTransition;
  /*
  Multiple factors determine ZeebePartition's health :
  * - servicesInstalled: indicates if role transition was successful and all services are installed
  * - diskSpaceAvailable
  * - if the partition is not blocked in a transition step
  */
  private boolean servicesInstalled;
  // We assume disk space is available until otherwise notified
  private boolean diskSpaceAvailable = true;

  public ZeebePartitionHealth(
      final int partitionId, final PartitionTransition partitionTransition) {
    name = "ZeebePartitionHealth-" + partitionId;
    this.partitionTransition = partitionTransition;
  }

  private void updateHealthStatus() {
    final HealthReport previousStatus = healthReport;
    if (previousStatus != null && previousStatus.isDead()) {
      return;
    }

    final var instant = Instant.now();
    final var partitionTransitionHealthIssue = partitionTransition.getHealthIssue();
    if (!diskSpaceAvailable) {
      healthReport =
          HealthReport.unhealthy(this).withMessage("Not enough disk space available", instant);
    } else if (partitionTransitionHealthIssue != null) {
      healthReport = HealthReport.unhealthy(this).withIssue(partitionTransitionHealthIssue);
    } else if (!servicesInstalled) {
      healthReport = HealthReport.unhealthy(this).withMessage("Services not installed", instant);
    } else {
      healthReport = HealthReport.healthy(this);
    }

    if (!Objects.equals(previousStatus, healthReport)) {
      failureListeners.forEach(l -> l.onHealthReport(healthReport));
    }
  }

  void setServicesInstalled(final boolean servicesInstalled) {
    this.servicesInstalled = servicesInstalled;
    updateHealthStatus();
  }

  void setDiskSpaceAvailable(final boolean diskSpaceAvailable) {
    this.diskSpaceAvailable = diskSpaceAvailable;
    updateHealthStatus();
  }

  void onUnrecoverableFailure(final Throwable error) {
    healthReport = HealthReport.dead(this).withIssue(error, Instant.now());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public HealthReport getHealthReport() {
    updateHealthStatus();
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
}
