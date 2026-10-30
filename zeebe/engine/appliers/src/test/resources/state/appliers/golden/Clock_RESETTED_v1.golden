/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableClockState;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;

public final class ClockResettedApplier implements TypedEventApplier<ClockIntent, ClockRecord> {

  private final MutableClockState clockState;

  public ClockResettedApplier(final MutableClockState clockState) {
    this.clockState = clockState;
  }

  @Override
  public void applyState(final long key, final ClockRecord value) {
    clockState.reset();
  }
}
