/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthStatus;
import java.util.HashSet;
import java.util.Set;

/**
 * Reflects the health of ZeebePartition. The health is updated by ZeebePartition when role
 * transitions either succeeded or failed.
 */
class ZeebePartitionHealth implements HealthMonitorable {

  private final String name;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private HealthStatus healthStatus = HealthStatus.UNHEALTHY;
  /*
  Multiple factors determine ZeebePartition's health :
  * - servicesInstalled: indicates if role transition was successful and all services are installed
  * - diskSpaceAvailable
  */
  private boolean servicesInstalled;
  // We assume disk space is available until otherwise notified
  private boolean diskSpaceAvailable = true;

  public ZeebePartitionHealth(final int partitionId) {
    name = "ZeebePartition-" + partitionId;
  }

  @Override
  public HealthStatus getHealthStatus() {
    return healthStatus;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    failureListeners.add(failureListener);
  }

  private void updateHealthStatus() {
    final var previousStatus = healthStatus;
    if (previousStatus == HealthStatus.DEAD) {
      return;
    }

    final boolean healthy = diskSpaceAvailable && servicesInstalled;
    if (healthy) {
      healthStatus = HealthStatus.HEALTHY;
    } else {
      healthStatus = HealthStatus.UNHEALTHY;
    }

    if (previousStatus != healthStatus) {
      switch (healthStatus) {
        case HEALTHY:
          failureListeners.forEach(FailureListener::onRecovered);
          break;
        case UNHEALTHY:
          failureListeners.forEach(FailureListener::onFailure);
          break;
        case DEAD:
          failureListeners.forEach(FailureListener::onUnrecoverableFailure);
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

  void onUnrecoverableFailure() {
    healthStatus = HealthStatus.DEAD;
  }

  public String getName() {
    return name;
  }
}
