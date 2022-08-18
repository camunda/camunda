/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import org.agrona.DirectBuffer;

public interface DeploymentState {

  /**
   * Returns whether there are any deployment distributions pending for a deployment.
   *
   * @param deploymentKey the key of the deployment that may have a pending distribution
   * @return {@code true} if a pending deployment for the deployment key exists, otherwise {@code
   *     false}.
   */
  boolean hasPendingDeploymentDistribution(long deploymentKey);

  /**
   * Returns whether a specific deployment distribution for a specific partition is pending.
   *
   * @param deploymentKey the key of the deployment that may have a pending distribution
   * @param partitionId the id of the partition to which the distribution might be pending
   * @return {@code true} if the specific pending deployment exists, otherwise {@code false}.
   */
  boolean hasPendingDeploymentDistribution(long deploymentKey, int partitionId);

  DeploymentRecord getStoredDeploymentRecord(long deploymentKey);

  void foreachPendingDeploymentDistribution(PendingDeploymentVisitor pendingDeploymentVisitor);

  @FunctionalInterface
  interface PendingDeploymentVisitor {
    void visit(final long deploymentKey, final int partitionId, final DirectBuffer directBuffer);
  }
}
