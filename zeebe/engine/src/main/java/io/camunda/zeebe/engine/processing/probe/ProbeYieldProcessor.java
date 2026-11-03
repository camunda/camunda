/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Makes an activated probe activatable again, for the next poll. Written by the gateway when it
 * could not deliver the activation response to the worker, as it hands back jobs it could not
 * deliver. There is no limit on yields: the deadline bounds the probe.
 */
@ExcludeAuthorizationCheck
public final class ProbeYieldProcessor implements TypedRecordProcessor<ProbeRecord> {

  private static final String NOT_ACTIVATED_MESSAGE =
      "Expected to yield activated probe with key '%d', but %s";

  private final ProbeState probeState;
  private final ProbeBehavior probeBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public ProbeYieldProcessor(
      final ProcessingState state, final Writers writers, final ProbeBehavior probeBehavior) {
    probeState = state.getProbeState();
    this.probeBehavior = probeBehavior;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<ProbeRecord> command) {
    final long probeKey = command.getKey();
    final var state = probeState.getState(probeKey);
    if (state != State.ACTIVATED) {
      final var reason =
          switch (state) {
            case NOT_FOUND -> "no such probe was found";
            default -> "it is not activated";
          };
      rejectionWriter.appendRejection(
          command,
          state == State.NOT_FOUND ? RejectionType.NOT_FOUND : RejectionType.INVALID_STATE,
          NOT_ACTIVATED_MESSAGE.formatted(probeKey, reason));
      return;
    }

    final var probe = probeState.getProbe(probeKey);
    stateWriter.appendFollowUpEvent(
        probeKey, ProbeIntent.YIELDED, ProbeBehavior.withoutInput(probe));
    probeBehavior.notifyProbesAvailable(probe.getProbeId());
  }
}
