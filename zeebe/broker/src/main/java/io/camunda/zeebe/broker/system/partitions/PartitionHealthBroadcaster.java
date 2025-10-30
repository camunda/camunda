/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;

/** Informs its delegate of partition health changes */
public final class PartitionHealthBroadcaster implements FailureListener {

  private final Integer partitionId;
  private final PartitionHealthListener delegate;

  public PartitionHealthBroadcaster(
      final Integer partitionId, final PartitionHealthListener delegate) {
    this.partitionId = partitionId;
    this.delegate = delegate;
  }

  @Override
  public void onFailure(final HealthReport report) {
    delegate.onHealthChanged(partitionId, HealthStatus.UNHEALTHY);
  }

  @Override
  public void onRecovered(final HealthReport report) {
    delegate.onHealthChanged(partitionId, HealthStatus.HEALTHY);
  }

  @Override
  public void onUnrecoverableFailure(final HealthReport report) {
    delegate.onHealthChanged(partitionId, HealthStatus.DEAD);
  }

  @FunctionalInterface
  public interface PartitionHealthListener {

    void onHealthChanged(final int partitionId, final HealthStatus status);
  }
}
