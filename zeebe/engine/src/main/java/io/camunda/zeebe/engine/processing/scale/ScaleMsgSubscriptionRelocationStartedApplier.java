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
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;

public class ScaleMsgSubscriptionRelocationStartedApplier
    implements TypedEventApplier<ScaleIntent, ScaleRecord> {

  private final RelocationState relocationState;

  public ScaleMsgSubscriptionRelocationStartedApplier(final RelocationState relocationState) {
    this.relocationState = relocationState;
  }

  @Override
  public void applyState(final long key, final ScaleRecord value) {
    relocationState.setRelocationStartedFor(value.getCorrelationKey());
  }
}
