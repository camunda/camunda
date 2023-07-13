/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import static io.camunda.zeebe.broker.Broker.LOG;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
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
  private HealthReport healthReport = HealthReport.unhealthy(this).withMessage("Initial state");
  private final PartitionTransitionContext partitionContext;
  /*
  Multiple factors determine ZeebePartition's health :
  * - servicesInstalled: indicates if role transition was successful and all services are installed
  * - diskSpaceAvailable
  * - if the partition is not blocked in a transition step
  */
  private boolean servicesInstalled;
  // We assume disk space is available until otherwise notified
  private boolean diskSpaceAvailable = true;
  private final long timeoutInMilliseconds = 60_000L;

  public ZeebePartitionHealth(
      final int partitionId, final PartitionTransitionContext partitionContext) {
    name = "ZeebePartition-" + partitionId;
    this.partitionContext = partitionContext;
  }

  private void updateHealthStatus() {
    final var previousStatus = healthReport;
    if (previousStatus.getStatus() == HealthStatus.DEAD) {
      return;
    }

    if (!diskSpaceAvailable) {
      healthReport = HealthReport.unhealthy(this).withMessage("Not enough disk space available");
    } else if (!servicesInstalled) {
      healthReport = HealthReport.unhealthy(this).withMessage("Services not installed");
    } else if (!Objects.isNull(partitionContext.getTimeOfLastTransitionStep())
        && System.currentTimeMillis()
            > partitionContext.getTimeOfLastTransitionStep() + timeoutInMilliseconds
        && partitionContext.getIsPartitionInTransition()) {
      // we check if the time since last transition step is larger than timeout
      // while if we're still in transition
      LOG.error(
          "Transition from {} on term {}, on transition step {} seems to be blocked."
              + " Timeout configured is {}s.",
          partitionContext.getCurrentRole(),
          partitionContext.getCurrentTerm(),
          partitionContext.getCurrentStepTransition(),
          timeoutInMilliseconds / 1000); // display timeout in seconds

      healthReport =
          HealthReport.unhealthy(this)
              .withMessage(
                  "Partition is blocked in Transition Step %s"
                      .formatted(partitionContext.getCurrentStepTransition()));
    } else {
      healthReport = HealthReport.healthy(this);
    }

    if (previousStatus != healthReport) {
      switch (healthReport.getStatus()) {
        case HEALTHY:
          failureListeners.forEach(FailureListener::onRecovered);
          break;
        case UNHEALTHY:
          failureListeners.forEach((l) -> l.onFailure(healthReport));
          break;
        case DEAD:
          failureListeners.forEach((l) -> l.onUnrecoverableFailure(healthReport));
          break;
        default:
          break;
      }
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
    healthReport = HealthReport.dead(this).withIssue(error);
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
