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
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceReexportRecord;
import io.camunda.zeebe.protocol.record.intent.ResourceReexportIntent;

public final class ResourceReexportStartedApplier
    implements TypedEventApplier<ResourceReexportIntent, ResourceReexportRecord> {

  private final MutableResourceState resourceState;

  public ResourceReexportStartedApplier(final MutableResourceState resourceState) {
    this.resourceState = resourceState;
  }

  @Override
  public void applyState(final long key, final ResourceReexportRecord value) {
    resourceState.markRpaReexportMigrationAsRan();
  }
}
