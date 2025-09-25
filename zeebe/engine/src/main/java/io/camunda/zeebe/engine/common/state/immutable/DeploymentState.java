/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

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

  /**
   * Returns true if all deployments are available to be read via {@link
   * #getStoredDeploymentRecord(long)}. Since we used to not store deployments, there may be brief
   * periods where we have not reconstructed all deployments yet. This method can be used to check
   * if all deployments are available.
   */
  boolean hasStoredAllDeployments();

  /**
   * Returns true when there is a deployment record stored for the given deployment key. Similar to
   * {@link #getStoredDeploymentRecord(long)} but doesn't return the record.
   */
  boolean hasStoredDeploymentRecord(long deploymentKey);

  DeploymentRecord getStoredDeploymentRecord(long deploymentKey);

  void foreachPendingDeploymentDistribution(PendingDeploymentVisitor pendingDeploymentVisitor);

  /**
   * Returns a copy of the first deployment that has a higher key than the provided one or null if
   * no such deployment exists.
   */
  DeploymentRecord nextDeployment(long previousDeploymentKey);

  @FunctionalInterface
  interface PendingDeploymentVisitor {
    void visit(final long deploymentKey, final int partitionId, final DirectBuffer directBuffer);
  }
}
