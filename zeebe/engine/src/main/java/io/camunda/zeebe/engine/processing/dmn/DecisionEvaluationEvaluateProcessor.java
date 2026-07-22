/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.dmn;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.collection.Tuple;

public class DecisionEvaluationEvaluateProcessor
    implements TypedRecordProcessor<DecisionEvaluationRecord> {

  private static final String ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED =
      "Expected either a decision id or a valid decision key, but none provided";
  private static final String ERROR_MESSAGE_DECISION_NOT_FOUND =
      "Expected to evaluate a decision with key '%d', but no such decision was found";

  private final DecisionBehavior decisionBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CslAuthorizationCheck cslCheck;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public DecisionEvaluationEvaluateProcessor(
      final DecisionBehavior decisionBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CslAuthorizationCheck cslCheck) {

    this.decisionBehavior = decisionBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.cslCheck = cslCheck;
  }

  @Override
  public void processRecord(final TypedRecord<DecisionEvaluationRecord> command) {

    final DecisionEvaluationRecord record = command.getValue();
    final var decisionOrFailure = getDecision(record);

    if (decisionOrFailure.isRight()) {
      final var decision = decisionOrFailure.get();
      final var decisionId = bufferAsString(decision.getDecisionId());
      final var authAndTenant =
          cslCheck.checkAuthorizationAndTenant(
              command,
              RequiredAuthorization.of(
                  b ->
                      b.resourceType(
                              AuthzModelMapper.fromProtocol(
                                  AuthorizationResourceType.DECISION_DEFINITION))
                          .permissionType(
                              AuthzModelMapper.fromProtocol(
                                  PermissionType.CREATE_DECISION_INSTANCE))
                          .resourceId(decisionId)),
              decision,
              AuthorizationRejectionMapper.forbidden(
                  PermissionType.CREATE_DECISION_INSTANCE,
                  AuthorizationResourceType.DECISION_DEFINITION),
              record.getTenantId(),
              new Rejection(
                  RejectionType.NOT_FOUND,
                  ERROR_MESSAGE_DECISION_NOT_FOUND.formatted(record.getDecisionKey())));
      if (authAndTenant.isLeft()) {
        final var rejection = authAndTenant.getLeft();
        responseWriter.writeRejectedResponseOnCommand(
            command, rejection.type(), rejection.reason());
        rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
        return;
      }
    }

    decisionOrFailure
        .flatMap(
            decision ->
                decisionBehavior
                    .findParsedDrgByDecision(decision)
                    .mapLeft(
                        failure -> new Rejection(RejectionType.NOT_FOUND, failure.getMessage())))
        .ifRightOrLeft(
            drg -> {
              final var decision = decisionOrFailure.get();
              final var variables = record.getVariablesBuffer();
              final var evaluationResult =
                  decisionBehavior.evaluateDecisionInDrg(
                      drg, BufferUtil.bufferAsString(decision.getDecisionId()), variables);

              final var evaluationRecordKey = keyGenerator.nextKey();
              final Tuple<DecisionEvaluationIntent, DecisionEvaluationRecord>
                  evaluationRecordTuple =
                      decisionBehavior.createDecisionEvaluationEvent(
                          decision, evaluationResult, evaluationRecordKey);
              stateWriter.appendFollowUpEvent(
                  evaluationRecordKey,
                  evaluationRecordTuple.getLeft(),
                  evaluationRecordTuple.getRight());
              responseWriter.writeAcceptedResponseOnCommand(
                  evaluationRecordKey,
                  evaluationRecordTuple.getLeft(),
                  evaluationRecordTuple.getRight(),
                  command);
            },
            rejection -> {
              final String reason = rejection.reason();
              responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), reason);
              rejectionWriter.appendRejection(command, rejection.type(), reason);
            });
  }

  private Either<Rejection, PersistedDecision> getDecision(final DecisionEvaluationRecord record) {

    final String decisionId = record.getDecisionId();
    final long decisionKey = record.getDecisionKey();

    if (!decisionId.isEmpty()) {
      return decisionBehavior
          .findLatestDecisionByIdAndTenant(decisionId, record.getTenantId())
          .mapLeft(failure -> new Rejection(RejectionType.NOT_FOUND, failure.getMessage()));
      // TODO: expand DecisionState API to find decisions by ID AND VERSION (#11230)
    } else if (decisionKey > -1L) {
      return decisionBehavior
          .findDecisionByKeyAndTenant(decisionKey, record.getTenantId())
          .mapLeft(failure -> new Rejection(RejectionType.NOT_FOUND, failure.getMessage()));
    } else {
      // if both ID and KEY are missing
      return Either.left(
          new Rejection(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED));
    }
  }
}
