/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;

/** Removes a probe that ended. */
public final class ProbeEndedApplier implements TypedEventApplier<ProbeIntent, ProbeRecord> {

  private final MutableProbeState probeState;

  public ProbeEndedApplier(final MutableProbeState probeState) {
    this.probeState = probeState;
  }

  @Override
  public void applyState(final long key, final ProbeRecord value) {
    probeState.remove(key);
  }
}
