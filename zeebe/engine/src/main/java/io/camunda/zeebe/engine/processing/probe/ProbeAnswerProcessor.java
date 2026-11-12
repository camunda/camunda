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
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.jspecify.annotations.NullMarked;

/**
 * Records a worker's answer to an activated probe and ends the probe. It checks in this order, so a
 * late answer with a well-formed body always gets {@code NOT_FOUND} (F4): the probe exists, the
 * sender holds {@code PROBE:ANSWER} on its probe ID and is assigned to its tenant, the envelope
 * (D48, D59), then the state. As for jobs, any holder of the permission may answer, not only the
 * worker that collected the probe (D24).
 *
 * <p>The answer has two requesters, the worker and the client, but a command carries one response:
 * this command answers the worker, and the internal {@code RESPOND} it appends carries the data to
 * the client (D74).
 */
@NullMarked
public final class ProbeAnswerProcessor implements TypedRecordProcessor<ProbeRecord> {

  static final int MAX_MESSAGE_LENGTH = 500;

  private static final String NOT_FOUND_MESSAGE =
      "Expected to answer probe with key '%d', but no such probe was found.";
  private static final String NOT_ACTIVATED_MESSAGE =
      "Expected to answer probe with key '%d', but it is in state '%s'.";

  private final ProbeState probeState;
  private final CslAuthorizationCheck cslCheck;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ProbeAnswerProcessor(
      final ProbeState probeState, final CslAuthorizationCheck cslCheck, final Writers writers) {
    this.probeState = probeState;
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

    final var checked =
        authorize(command, probe)
            .flatMap(ignored -> validateEnvelope(command.getValue()))
            .flatMap(ignored -> checkActivated(probeKey));
    if (checked.isLeft()) {
      reject(command, checked.getLeft().type(), checked.getLeft().reason());
      return;
    }

    final var answer = command.getValue();
    // never the data (D42): only the RESPOND command carries it, to the client's response
    final var answered = ProbeBehavior.withoutInput(probe).setOutcome(answer.getOutcome());
    if (answer.getOutcome() == ProbeOutcome.ERROR) {
      answered.setMessage(answer.getMessage());
    }
    stateWriter.appendFollowUpEvent(probeKey, ProbeIntent.ANSWERED, answered);
    responseWriter.writeAcceptedResponseOnCommand(
        probeKey, ProbeIntent.ANSWERED, answered, command);
    final var response = new ProbeRecord().copyFrom(answered).setData(answer.getDataBuffer());
    commandWriter.appendFollowUpCommand(probeKey, ProbeIntent.RESPOND, response);
  }

  /** A worker of another tenant gets NOT_FOUND, so it does not learn that the probe exists. */
  private Either<Rejection, ProbeRecord> authorize(
      final TypedRecord<ProbeRecord> command, final ProbeRecord probe) {
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

  /**
   * The envelope only (D48): the data is opaque. The engine cannot tell an empty data object from
   * no data, as the record's default is an empty document, so an empty one counts as none.
   */
  private static Either<Rejection, ProbeRecord> validateEnvelope(final ProbeRecord answer) {
    final boolean hasData = !isEmptyDocument(answer);
    final boolean hasMessage = !answer.getMessage().isEmpty();
    final var valid =
        switch (answer.getOutcome()) {
          case OK -> hasData && !hasMessage;
          case UNSUPPORTED -> !hasData && !hasMessage;
          case ERROR ->
              !hasData && hasMessage && answer.getMessage().length() <= MAX_MESSAGE_LENGTH;
          case UNSPECIFIED -> false;
        };
    if (valid) {
      return Either.right(answer);
    }
    return Either.left(
        new Rejection(
            RejectionType.INVALID_ARGUMENT,
            ("Expected an answer of outcome OK with data, UNSUPPORTED with neither data nor message,"
                    + " or ERROR with a message of at most %d characters, but got outcome '%s'"
                    + " %s data and %s message of %d characters.")
                .formatted(
                    MAX_MESSAGE_LENGTH,
                    answer.getOutcome(),
                    hasData ? "with" : "without",
                    hasMessage ? "a" : "no",
                    answer.getMessage().length())));
  }

  private Either<Rejection, State> checkActivated(final long probeKey) {
    final var state = probeState.getState(probeKey);
    return state == State.ACTIVATED
        ? Either.right(state)
        : Either.left(
            new Rejection(
                RejectionType.INVALID_STATE, NOT_ACTIVATED_MESSAGE.formatted(probeKey, state)));
  }

  private static boolean isEmptyDocument(final ProbeRecord answer) {
    final var data = answer.getDataBuffer();
    return data.capacity() == 0 || BufferUtil.equals(data, DocumentValue.EMPTY_DOCUMENT);
  }

  private void reject(
      final TypedRecord<ProbeRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectedResponseOnCommand(command, type, reason);
  }
}
