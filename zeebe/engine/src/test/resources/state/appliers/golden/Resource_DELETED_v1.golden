/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableResourceState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;

public class ResourceDeletedApplier implements TypedEventApplier<ResourceIntent, ResourceRecord> {

  private final MutableResourceState resourceState;

  public ResourceDeletedApplier(final MutableResourceState resourceState) {
    this.resourceState = resourceState;
  }

  @Override
  public void applyState(final long key, final ResourceRecord value) {
    resourceState.deleteResourceInResourcesColumnFamily(value);
    resourceState.deleteResourceInResourceByIdAndVersionColumnFamily(value);
    resourceState.deleteResourceInResourceVersionColumnFamily(value);
    resourceState.deleteResourceInResourceKeyByResourceIdAndDeploymentKeyColumnFamily(value);
    resourceState.deleteResourceInResourceKeyByResourceIdAndVersionTagColumnFamily(value);
  }
}
