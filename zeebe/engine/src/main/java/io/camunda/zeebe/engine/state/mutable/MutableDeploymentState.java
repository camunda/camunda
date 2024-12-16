/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;

public interface MutableDeploymentState extends DeploymentState {

  void addPendingDeploymentDistribution(long deploymentKey, int partition);

  void removePendingDeploymentDistribution(long key, int partitionId);

  void storeDeploymentRecord(long key, DeploymentRecord value);

  void removeDeploymentRecord(long key);

  /**
   * Marks all deployments as stored. After this has been called, {@link
   * DeploymentState#hasStoredAllDeployments()} returns true.
   */
  void markAllDeploymentsAsStored();
}
