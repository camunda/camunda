/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.DeploymentState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;

public interface MutableDeploymentState extends DeploymentState {

  void addPendingDeploymentDistribution(long deploymentKey, int partition);

  void removePendingDeploymentDistribution(long key, int partitionId);

  void storeDeploymentRecord(long key, DeploymentRecord value);

  void removeDeploymentRecord(long key);
}
