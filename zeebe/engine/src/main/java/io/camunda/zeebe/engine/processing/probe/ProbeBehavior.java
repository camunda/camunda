/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.engine.processing.streamprocessor.ProbeNotifier;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import org.jspecify.annotations.NullMarked;

/**
 * What several probe processors do: announce that a probe ID has a probe to collect, and end a
 * probe without an answer, answering the client that waits for it.
 */
@NullMarked
public final class ProbeBehavior {

  private final ProbeNotifier probeNotifier;
  private final AsyncRequestState asyncRequestState;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final SideEffectWriter sideEffectWriter;

  public ProbeBehavior(
      final ProbeNotifier probeNotifier,
      final AsyncRequestState asyncRequestState,
      final Writers writers) {
    this.probeNotifier = probeNotifier;
    this.asyncRequestState = asyncRequestState;
    stateWriter = writers.state();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
  }

  /** Tells the gateways that the probe ID has a probe to collect, once the events are written. */
  public void notifyProbesAvailable(final String probeId) {
    sideEffectWriter.appendSideEffect(() -> probeNotifier.notifyProbesAvailable(probeId));
  }

  /**
   * Ends the probe with {@code TIMED_OUT} and answers the client that waits for it, if it still
   * waits: a client that is gone, e.g. because the gateway restarted, gets no answer, as for any
   * other async request.
   */
  public void timeOut(final long probeKey, final ProbeRecord probe, final ProbeEndReason reason) {
    final var timedOut = ended(probe, reason);
    stateWriter.appendFollowUpEvent(probeKey, ProbeIntent.TIMED_OUT, timedOut);
    asyncRequestState
        .findRequest(probeKey, ValueType.PROBE, ProbeIntent.CREATE)
        .ifPresent(
            request -> {
              responseWriter.writeAcceptedResponse(
                  probeKey,
                  ProbeIntent.TIMED_OUT,
                  timedOut,
                  ValueType.PROBE,
                  request.requestId(),
                  request.requestStreamId());
              stateWriter.appendFollowUpEvent(
                  request.key(), AsyncRequestIntent.PROCESSED, request.record());
            });
  }

  /**
   * The event that ends the probe: what identifies it and why it ended, without the input, which
   * the {@code CREATED} event already carries.
   */
  static ProbeRecord ended(final ProbeRecord probe, final ProbeEndReason reason) {
    return new ProbeRecord()
        .setProbeId(probe.getProbeId())
        .setTenantId(probe.getTenantId())
        .setRequestTimeout(probe.getRequestTimeout())
        .setDeadline(probe.getDeadline())
        .setWorker(probe.getWorker())
        .setEndReason(reason)
        .setMessage(endMessage(probe, reason));
  }

  private static String endMessage(final ProbeRecord probe, final ProbeEndReason reason) {
    return switch (reason) {
      // a poll skips the probe IDs its worker may not answer, so both causes look the same here
      case NO_WORKER ->
          "No worker for '%s' collected the probe within %d ms. Check that a worker polls this probe ID and holds PROBE:ANSWER on it, then retry."
              .formatted(probe.getProbeId(), probe.getRequestTimeout());
      case SECRET_RESOLUTION_TIMEOUT ->
          "The secrets of the probe for '%s' were not resolved within %d ms."
              .formatted(probe.getProbeId(), probe.getRequestTimeout());
      case WORKER_DID_NOT_ANSWER ->
          "The worker '%s' collected the probe for '%s' but did not answer within %d ms."
              .formatted(probe.getWorker(), probe.getProbeId(), probe.getRequestTimeout());
      case SECRET_RESOLUTION_ERROR, UNSPECIFIED ->
          throw new IllegalArgumentException(
              "Expected a reason to time out a probe, but got '%s'".formatted(reason));
    };
  }
}
