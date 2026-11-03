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
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;

/** Activates the probes a poll collected, recording the worker of the poll on each. */
public final class ProbeBatchActivatedApplier
    implements TypedEventApplier<ProbeBatchIntent, ProbeBatchRecord> {

  private final MutableProbeState probeState;

  public ProbeBatchActivatedApplier(final MutableProbeState probeState) {
    this.probeState = probeState;
  }

  @Override
  public void applyState(final long key, final ProbeBatchRecord value) {
    final var worker = value.getWorker();
    for (final LongValue probeKey : value.probeKeys()) {
      probeState.activate(probeKey.getValue(), worker);
    }
  }
}
