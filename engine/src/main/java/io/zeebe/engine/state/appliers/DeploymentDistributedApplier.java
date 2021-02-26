/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.intent.DeploymentIntent;

public class DeploymentDistributedApplier
    implements TypedEventApplier<DeploymentIntent, DeploymentRecord> {

  private final MutableWorkflowState mutableWorkflowState;

  public DeploymentDistributedApplier(final MutableWorkflowState mutableWorkflowState) {
    this.mutableWorkflowState = mutableWorkflowState;
  }

  @Override
  public void applyState(final long key, final DeploymentRecord value) {
    mutableWorkflowState.putDeployment(value);
  }
}
