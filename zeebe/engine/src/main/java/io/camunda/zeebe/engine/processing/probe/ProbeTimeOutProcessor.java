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
import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;
import org.jspecify.annotations.NullMarked;

/**
 * Ends a probe at its deadline, on the command of {@link ProbeTimeoutCheckScheduler}. The deadline
 * covers waiting for a poll, for secrets and for the worker, so the reason says which one ran out.
 */
@NullMarked
@ExcludeAuthorizationCheck
public final class ProbeTimeOutProcessor implements TypedRecordProcessor<ProbeRecord> {

  private static final String NOT_TIMED_OUT_MESSAGE =
      "Expected to time out probe with key '%d', but %s";

  private final ProbeState probeState;
  private final ProbeBehavior probeBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final InstantSource clock;

  public ProbeTimeOutProcessor(
      final ProbeState probeState,
      final ProbeBehavior probeBehavior,
      final Writers writers,
      final InstantSource clock) {
    this.probeState = probeState;
    this.probeBehavior = probeBehavior;
    rejectionWriter = writers.rejection();
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<ProbeRecord> command) {
    final long probeKey = command.getKey();
    final var state = probeState.getState(probeKey);
    final var probe = probeState.getProbe(probeKey);
    if (state == State.NOT_FOUND || probe == null) {
      reject(command, "no such probe was found");
      return;
    }
    if (probe.getDeadline() > clock.millis()) {
      reject(command, "it has not timed out");
      return;
    }

    final var reason =
        switch (state) {
          case ACTIVATABLE -> ProbeEndReason.NO_WORKER;
          case WAITING_FOR_SECRET_RESOLUTION -> ProbeEndReason.SECRET_RESOLUTION_TIMEOUT;
          case ACTIVATED -> ProbeEndReason.WORKER_DID_NOT_ANSWER;
          case NOT_FOUND -> throw new IllegalStateException("Rejected above");
        };
    probeBehavior.timeOut(probeKey, probe, reason);
  }

  private void reject(final TypedRecord<ProbeRecord> command, final String reason) {
    rejectionWriter.appendRejection(
        command,
        RejectionType.NOT_FOUND,
        NOT_TIMED_OUT_MESSAGE.formatted(command.getKey(), reason));
  }
}
