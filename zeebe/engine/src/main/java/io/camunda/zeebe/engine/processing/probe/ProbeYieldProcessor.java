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
 * Takes a probe back from a worker it could not be pushed to and pushes it once more, to another
 * worker. Written by the broker when a push fails on every connection of the subscription, as
 * {@code YieldingJobStreamErrorHandler} yields a job. A probe is re-dispatched at most once.
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
    if (probe.getYieldCount() > 0) {
      probeBehavior.cancelNoWorker(probeKey, probe);
      return;
    }

    final var excludedWorker = probe.getWorker();
    final var stream = probeBehavior.findStream(probe, excludedWorker);
    if (stream.isEmpty()) {
      probeBehavior.cancelNoWorker(probeKey, probe);
      return;
    }

    stateWriter.appendFollowUpEvent(
        probeKey, ProbeIntent.YIELDED, ProbeBehavior.withoutInput(probe));
    probeBehavior.activate(probeKey, probeState.getProbe(probeKey), stream.get());
  }
}
