/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import com.google.common.collect.ImmutableMap;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;

/**
 * Stands in for a partition's {@link ZeebePartition} in the broker's {@link HealthMonitor} while
 * the partition group is in recovery mode, so that the broker reports healthy during a recovery
 * that is going well and surfaces a partition whose recovery failed. Reuses the {@link
 * ZeebePartition#componentName(PartitionId)} so the partition occupies the same slot in the health
 * component tree across mode transitions, overwriting any stale placeholder the processing-mode
 * bootstrap registration may have left behind.
 *
 * <p>The reported status is fixed at construction: a recovery partition either started (healthy for
 * the duration of recovery mode) or failed to start with nothing left running to bring it back
 * (dead). Failure listeners are therefore never invoked.
 */
final class RecoveringPartitionHealth implements HealthMonitorable {

  private final String componentName;
  private final HealthReport healthReport;

  private RecoveringPartitionHealth(final String componentName, final HealthReport healthReport) {
    this.componentName = componentName;
    this.healthReport = healthReport;
  }

  static RecoveringPartitionHealth recovered(final PartitionId partitionId) {
    final var componentName = ZeebePartition.componentName(partitionId);
    return new RecoveringPartitionHealth(
        componentName,
        new HealthReport(componentName, HealthStatus.HEALTHY, null, ImmutableMap.of()));
  }

  static RecoveringPartitionHealth failed(final PartitionId partitionId) {
    final var componentName = ZeebePartition.componentName(partitionId);
    return new RecoveringPartitionHealth(
        componentName,
        new HealthReport(componentName, HealthStatus.DEAD, null, ImmutableMap.of())
            .withMessage(
                "Partition %s failed to recover and will stay dead until the restore is retried"
                    .formatted(partitionId),
                Instant.now()));
  }

  @Override
  public String componentName() {
    return componentName;
  }

  @Override
  public HealthReport getHealthReport() {
    return healthReport;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    // the status never changes, so there is nothing to notify
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    // no listeners are ever stored
  }
}
