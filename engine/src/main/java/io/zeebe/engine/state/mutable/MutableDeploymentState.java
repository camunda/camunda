/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.processing.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.state.immutable.DeploymentState;

public interface MutableDeploymentState extends DeploymentState {

  void putPendingDeployment(long key, PendingDeploymentDistribution pendingDeploymentDistribution);

  PendingDeploymentDistribution removePendingDeployment(long key);

  void addPendingDeploymentDistribution(long deploymentKey, int partition);
}
