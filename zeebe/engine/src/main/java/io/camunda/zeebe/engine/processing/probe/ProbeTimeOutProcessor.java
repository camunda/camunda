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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;

/**
 * Ends a probe whose deadline passed, written by {@link ProbeTimeoutCheckScheduler}. The deadline
 * covers waiting for a poll, for secrets and for the worker, so the reason says which one ran out.
 */
@ExcludeAuthorizationCheck
public final class ProbeTimeOutProcessor implements TypedRecordProcessor<ProbeRecord> {

  private final ProbeState probeState;
  private final ProbeBehavior probeBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final InstantSource clock;

  public ProbeTimeOutProcessor(
      final ProcessingState state,
      final Writers writers,
      final ProbeBehavior probeBehavior,
      final InstantSource clock) {
    probeState = state.getProbeState();
    this.probeBehavior = probeBehavior;
    rejectionWriter = writers.rejection();
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<ProbeRecord> command) {
    final long probeKey = command.getKey();
    final var probe = probeState.getProbe(probeKey);
    if (probe == null || probe.getDeadline() > clock.millis()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected to time out probe with key '%d', but %s"
              .formatted(
                  probeKey, probe == null ? "no such probe was found" : "it has not timed out"));
      return;
    }

    // the state at the deadline says what ran out: no poll collected it, its secrets were not
    // resolved, or the worker that collected it did not answer
    switch (probeState.getState(probeKey)) {
      case ACTIVATABLE ->
          probeBehavior.end(
              probeKey,
              probe,
              ProbeIntent.TIMED_OUT,
              ProbeEndReason.NO_WORKER,
              ProbeBehavior.noWorkerMessage(probe));
      case WAITING_FOR_SECRET_RESOLUTION ->
          probeBehavior.end(
              probeKey,
              probe,
              ProbeIntent.TIMED_OUT,
              ProbeEndReason.SECRET_RESOLUTION_TIMEOUT,
              "The secrets of the probe were not resolved before its deadline.");
      default ->
          probeBehavior.end(
              probeKey,
              probe,
              ProbeIntent.TIMED_OUT,
              ProbeEndReason.WORKER_DID_NOT_ANSWER,
              "The worker '%s' did not answer before the deadline.".formatted(probe.getWorker()));
    }
  }
}
