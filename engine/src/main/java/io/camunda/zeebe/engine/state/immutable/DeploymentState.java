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

  boolean hasPendingDeploymentDistribution(long deploymentKey);

  boolean hasPendingDeploymentDistribution(long deploymentKey, int partitionId);

  DeploymentRecord getStoredDeploymentRecord(long deploymentKey);

  void foreachPendingDeploymentDistribution(PendingDeploymentVisitor pendingDeploymentVisitor);

  @FunctionalInterface
  interface PendingDeploymentVisitor {
    void visit(final long deploymentKey, final int partitionId, final DirectBuffer directBuffer);
  }
}
