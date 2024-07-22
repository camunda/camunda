/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scale;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.protocol.record.value.ScaleRecordValue;

public class ScaleRelocateMessagesStartedApplier
    implements TypedEventApplier<ScaleIntent, ScaleRecordValue> {

  private final RelocationState relocationState;

  public ScaleRelocateMessagesStartedApplier(final RelocationState relocationState) {
    this.relocationState = relocationState;
  }

  @Override
  public void applyState(final long key, final ScaleRecordValue value) {
    relocationState.setRoutingInfo(value.getRoutingInfo());
    relocationState.setRelocationStarted();
  }
}
