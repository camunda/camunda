/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.immutable.ProbeState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Records a worker's answer. The answer has two requesters, the worker and the client waiting for
 * the probe, but a command carries one response: this processor answers the worker and appends
 * {@link ProbeIntent#RESPOND}, whose processor answers the client (as {@code JobCompleteProcessor}
 * hands a task listener's completion to the user task).
 */
public final class ProbeAnswerProcessor implements TypedRecordProcessor<ProbeRecord> {

  private static final String NOT_FOUND_MESSAGE =
      "Expected to answer probe with key '%d', but no such probe was found.";
  private static final String NOT_ACTIVATED_MESSAGE =
      "Expected to answer probe with key '%d', but it was not pushed to a worker yet.";

  private final ProbeState probeState;
  private final CslAuthorizationCheck cslCheck;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ProbeAnswerProcessor(
      final ProcessingState state, final Writers writers, final CslAuthorizationCheck cslCheck) {
    probeState = state.getProbeState();
    this.cslCheck = cslCheck;
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<ProbeRecord> command) {
    final long probeKey = command.getKey();
    final var probe = probeState.getProbe(probeKey);
    if (probe == null) {
      reject(command, RejectionType.NOT_FOUND, NOT_FOUND_MESSAGE.formatted(probeKey));
      return;
    }

    final var authorization = authorize(command, probe);
    if (authorization.isLeft()) {
      final var rejection = authorization.getLeft();
      reject(command, rejection.type(), rejection.reason());
      return;
    }

    final var invalid = ProbeAnswerValidator.validate(command.getValue());
    if (invalid.isPresent()) {
      reject(command, RejectionType.INVALID_ARGUMENT, invalid.get());
      return;
    }

    if (probeState.getState(probeKey) != State.ACTIVATED) {
      reject(command, RejectionType.INVALID_STATE, NOT_ACTIVATED_MESSAGE.formatted(probeKey));
      return;
    }

    final var answer = command.getValue();
    final var answered = ProbeBehavior.withoutInput(probe).setOutcome(answer.getOutcome());
    if (answer.getOutcome() == ProbeOutcome.ERROR) {
      answered.setMessage(answer.getMessage());
    }
    stateWriter.appendFollowUpEvent(probeKey, ProbeIntent.ANSWERED, answered);
    responseWriter.writeAcceptedResponseOnCommand(
        probeKey, ProbeIntent.ANSWERED, answered, command);

    // the data goes to the client only: an answer can be large or carry business data, so it
    // stays off the exported ANSWERED event, and the exporters skip the RESPOND command
    final var response = answered.copy();
    if (answer.getOutcome() == ProbeOutcome.OK) {
      response.setData(answer.getDataBuffer());
    }
    commandWriter.appendFollowUpCommand(probeKey, ProbeIntent.RESPOND, response);
  }

  private io.camunda.zeebe.util.Either<Rejection, ProbeRecord> authorize(
      final TypedRecord<ProbeRecord> command, final ProbeRecord probe) {
    // a worker of another tenant must not learn that the probe exists, as for job commands
    return cslCheck.checkAuthorizationAndTenant(
        command,
        RequiredAuthorization.of(
            b ->
                b.resourceType(AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROBE))
                    .permissionType(AuthzModelMapper.fromProtocol(PermissionType.ANSWER))
                    .resourceId(probe.getProbeId())),
        probe,
        AuthorizationRejectionMapper.noPrincipal(),
        probe.getTenantId(),
        new Rejection(RejectionType.NOT_FOUND, NOT_FOUND_MESSAGE.formatted(command.getKey())));
  }

  private void reject(
      final TypedRecord<ProbeRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectedResponseOnCommand(command, type, reason);
  }
}
