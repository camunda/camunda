/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableDeploymentState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;

public class DeploymentReconstructedAllApplier
    implements TypedEventApplier<DeploymentIntent, DeploymentRecord> {
  private final MutableDeploymentState deploymentState;

  public DeploymentReconstructedAllApplier(final MutableDeploymentState deploymentState) {
    this.deploymentState = deploymentState;
  }

  @Override
  public void applyState(final long key, final DeploymentRecord value) {
    deploymentState.markAllDeploymentsAsStored();
  }
}
