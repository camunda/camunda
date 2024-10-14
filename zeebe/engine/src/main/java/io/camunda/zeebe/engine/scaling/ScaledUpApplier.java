/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;

public class ScaledUpApplier implements TypedEventApplier<ScaleIntent, ScaleRecord> {
  private final MutableRoutingState routingState;

  public ScaledUpApplier(final MutableRoutingState routingState) {
    this.routingState = routingState;
  }

  @Override
  public void applyState(final long key, final ScaleRecord value) {
    routingState.arriveAtDesiredState();
  }
}
