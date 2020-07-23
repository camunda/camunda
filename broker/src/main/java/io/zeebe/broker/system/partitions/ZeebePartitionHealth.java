/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import io.zeebe.util.health.FailureListener;
import io.zeebe.util.health.HealthMonitorable;
import io.zeebe.util.health.HealthStatus;

/**
 * Reflects the health of ZeebePartition. The health is updated by ZeebePartition when role
 * transitions either succeeded or failed.
 */
class ZeebePartitionHealth implements HealthMonitorable {
  private HealthStatus healthStatus = HealthStatus.UNHEALTHY;
  private final String name;
  private FailureListener failureListener;

  public ZeebePartitionHealth(final int partitionId) {
    this.name = "ZeebePartition-" + partitionId;
  }

  @Override
  public HealthStatus getHealthStatus() {
    return healthStatus;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    this.failureListener = failureListener;
  }

  void setHealthStatus(final HealthStatus healthStatus) {
    final var previousStatus = this.healthStatus;
    this.healthStatus = healthStatus;

    if (previousStatus != healthStatus && failureListener != null) {
      switch (healthStatus) {
        case HEALTHY:
          failureListener.onRecovered();
          break;
        case UNHEALTHY:
          failureListener.onFailure();
          break;
        default:
          break;
      }
    }
  }

  public String getName() {
    return name;
  }
}
