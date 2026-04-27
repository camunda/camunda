/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.expression.ExpressionValidator.ValidatedCommand;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

public final class ExpressionEvaluateProcessor implements TypedRecordProcessor<ExpressionRecord> {

  private final ExpressionBehavior expressionBehavior;
  private final ExpressionValidator validator;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ExpressionEvaluateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ExpressionBehavior expressionBehavior,
      final ExpressionValidator validator,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.expressionBehavior = expressionBehavior;
    this.validator = validator;
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<ExpressionRecord> command) {
    validator
        .validate(command)
        .flatMap(validated -> authorize(command, validated))
        .flatMap(this::evaluate)
        .ifRightOrLeft(
            resolvedRecord -> acceptCommand(command, resolvedRecord),
            rejection -> rejectCommand(command, rejection));
  }

  private Either<Rejection, ValidatedCommand> authorize(
      final TypedRecord<ExpressionRecord> command, final ValidatedCommand validated) {
    return isAuthorized(command, validated.record()).map(ignored -> validated);
  }

  private Either<Rejection, ExpressionRecord> evaluate(final ValidatedCommand validated) {
    return expressionBehavior.resolveExpression(validated.expression(), validated.record());
  }

  private Either<Rejection, ExpressionRecord> isAuthorized(
      final TypedRecord<ExpressionRecord> command, final ExpressionRecord record) {
    final var tenantId = record.getTenantId();
    final var authRequestBuilder =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.EXPRESSION)
            .permissionType(PermissionType.EVALUATE);

    if (tenantId != null && !tenantId.isEmpty()) {
      authRequestBuilder.tenantId(tenantId);
    }

    final var authRequest = authRequestBuilder.build();
    return authCheckBehavior.isAuthorizedOrInternalCommand(authRequest).map(unused -> record);
  }

  private void rejectCommand(
      final TypedRecord<ExpressionRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
  }

  private void acceptCommand(
      final TypedRecord<ExpressionRecord> command, final ExpressionRecord resolvedValue) {
    final var key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, ExpressionIntent.EVALUATED, resolvedValue);
    responseWriter.writeEventOnCommand(key, ExpressionIntent.EVALUATED, resolvedValue, command);
  }
}
