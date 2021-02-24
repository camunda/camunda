/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.mutable.MutableDeploymentState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.intent.DeploymentIntent;

public class DeploymentCreatedApplier
    implements TypedEventApplier<DeploymentIntent, DeploymentRecord> {

  private final MutableDeploymentState deploymentState;

  public DeploymentCreatedApplier(final ZeebeState zeebeState) {
    deploymentState = zeebeState.getDeploymentState();
  }

  @Override
  public void applyState(final long key, final DeploymentRecord value) {
    deploymentState.storeDeploymentRecord(key, value);
  }
}
