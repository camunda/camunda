/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;
import org.jspecify.annotations.NullMarked;

/**
 * Makes an activated probe activatable again and announces it, for the next poll. The gateway sends
 * it for the probes it could not deliver to the worker (D41), as for jobs. There is no limit on
 * yields: the deadline bounds the probe.
 */
@NullMarked
@ExcludeAuthorizationCheck
public final class ProbeYieldProcessor implements TypedRecordProcessor<ProbeRecord> {

  private static final String NOT_FOUND_MESSAGE =
      "Expected to yield probe with key '%d', but no such probe was found.";
  private static final String NOT_ACTIVATED_MESSAGE =
      "Expected to yield probe with key '%d', but it is in state '%s'.";
  private static final String EXPIRED_MESSAGE =
      "Expected to yield probe with key '%d', but its deadline has passed.";

  private final ProbeState probeState;
  private final ProbeBehavior probeBehavior;
  private final CslTenantCheck tenantCheck;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final InstantSource clock;

  public ProbeYieldProcessor(
      final ProbeState probeState,
      final ProbeBehavior probeBehavior,
      final CslTenantCheck tenantCheck,
      final Writers writers,
      final InstantSource clock) {
    this.probeState = probeState;
    this.probeBehavior = probeBehavior;
    this.tenantCheck = tenantCheck;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<ProbeRecord> command) {
    final long probeKey = command.getKey();
    final var state = probeState.getState(probeKey);
    final var probe = probeState.getProbe(probeKey);
    if (state == State.NOT_FOUND || probe == null || !isOfAuthorizedTenant(command, probe)) {
      reject(command, RejectionType.NOT_FOUND, NOT_FOUND_MESSAGE.formatted(probeKey));
      return;
    }
    // late, as an answer (F4): the probe stays with its worker until the queued TIME_OUT ends it
    if (probe.getDeadline() <= clock.millis()) {
      reject(command, RejectionType.NOT_FOUND, EXPIRED_MESSAGE.formatted(probeKey));
      return;
    }
    if (state != State.ACTIVATED) {
      reject(
          command, RejectionType.INVALID_STATE, NOT_ACTIVATED_MESSAGE.formatted(probeKey, state));
      return;
    }

    final var yielded = ProbeBehavior.withoutInput(probe);
    stateWriter.appendFollowUpEvent(probeKey, ProbeIntent.YIELDED, yielded);
    // the gateway sends YIELD as a request: without a response, it waits for its time-out
    responseWriter.writeAcceptedResponseOnCommand(probeKey, ProbeIntent.YIELDED, yielded, command);
    probeBehavior.notifyProbesAvailable(probe.getProbeId());
  }

  private void reject(
      final TypedRecord<ProbeRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectedResponseOnCommand(command, type, reason);
  }

  /**
   * As for jobs, a worker of another tenant does not learn that the probe exists. A yield the
   * broker writes itself carries no claims, so it is not filtered.
   */
  private boolean isOfAuthorizedTenant(
      final TypedRecord<ProbeRecord> command, final ProbeRecord probe) {
    if (command.isInternalCommand()) {
      return true;
    }
    final var authorizedTenants = tenantCheck.resolveAuthorizedTenants(command);
    return authorizedTenants.wildcard()
        || authorizedTenants.isAuthorizedForTenantId(probe.getTenantId());
  }
}
