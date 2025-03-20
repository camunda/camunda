/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.dmn;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.collection.Tuple;

public class DecisionEvaluationEvaluteProcessor
    implements TypedRecordProcessor<DecisionEvaluationRecord> {

  private static final String ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED =
      "Expected either a decision id or a valid decision key, but none provided";

  private final DecisionBehavior decisionBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public DecisionEvaluationEvaluteProcessor(
      final DecisionBehavior decisionBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {

    this.decisionBehavior = decisionBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<DecisionEvaluationRecord> command) {

    final DecisionEvaluationRecord record = command.getValue();
    final var decisionOrFailure = getDecision(record);

    if (decisionOrFailure.isRight()) {
      final var decision = decisionOrFailure.get();
      final var decisionId = bufferAsString(decision.getDecisionId());
      final var authRequest =
          new AuthorizationRequest(
                  command,
                  AuthorizationResourceType.DECISION_DEFINITION,
                  PermissionType.CREATE_DECISION_INSTANCE,
                  record.getTenantId())
              .addResourceId(decisionId);

      final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
      if (isAuthorized.isLeft()) {
        final var rejection = isAuthorized.getLeft();
        final String errorMessage =
            RejectionType.NOT_FOUND.equals(rejection.type())
                ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                    "evaluate a decision", record.getDecisionKey(), "such decision")
                : rejection.reason();
        responseWriter.writeRejectionOnCommand(command, rejection.type(), errorMessage);
        rejectionWriter.appendRejection(command, rejection.type(), errorMessage);
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

              final Tuple<DecisionEvaluationIntent, DecisionEvaluationRecord>
                  evaluationRecordTuple =
                      decisionBehavior.createDecisionEvaluationEvent(decision, evaluationResult);

              final var evaluationRecordKey = keyGenerator.nextKey();
              stateWriter.appendFollowUpEvent(
                  evaluationRecordKey,
                  evaluationRecordTuple.getLeft(),
                  evaluationRecordTuple.getRight());
              responseWriter.writeEventOnCommand(
                  evaluationRecordKey,
                  evaluationRecordTuple.getLeft(),
                  evaluationRecordTuple.getRight(),
                  command);
            },
            rejection -> {
              final String reason = rejection.reason();
              responseWriter.writeRejectionOnCommand(command, rejection.type(), reason);
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
