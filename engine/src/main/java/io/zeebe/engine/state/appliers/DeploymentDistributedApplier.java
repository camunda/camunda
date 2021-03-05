/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableProcessState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.intent.DeploymentIntent;

public class DeploymentDistributedApplier
    implements TypedEventApplier<DeploymentIntent, DeploymentRecord> {

  private final MutableProcessState mutableProcessState;

  public DeploymentDistributedApplier(final MutableProcessState mutableProcessState) {
    this.mutableProcessState = mutableProcessState;
  }

  @Override
  public void applyState(final long key, final DeploymentRecord value) {
    mutableProcessState.putDeployment(value);
  }
}
