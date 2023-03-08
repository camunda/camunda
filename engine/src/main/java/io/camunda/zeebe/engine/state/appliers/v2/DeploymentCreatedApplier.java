/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers.v2;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;

public class DeploymentCreatedApplier
    implements TypedEventApplier<DeploymentIntent, DeploymentRecord> {

  public DeploymentCreatedApplier() {}

  @Override
  public void applyState(final long key, final DeploymentRecord value) {}
}
