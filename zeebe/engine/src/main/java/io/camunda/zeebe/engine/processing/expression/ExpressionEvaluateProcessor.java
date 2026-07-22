/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.expression.ExpressionValidator.ResolvedInstance;
import io.camunda.zeebe.engine.processing.expression.ExpressionValidator.ValidatedCommand;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

public final class ExpressionEvaluateProcessor implements TypedRecordProcessor<ExpressionRecord> {

  private static final String ERROR_MESSAGE_NOT_FOUND_FOR_TENANT =
      "Expected to perform operation '%s' on resource '%s', but no resource was found for tenant '%s'";

  private final ExpressionBehavior expressionBehavior;
  private final ExpressionValidator validator;
  private final CslAuthorizationCheck cslCheck;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ExpressionEvaluateProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ExpressionBehavior expressionBehavior,
      final ExpressionValidator validator,
      final CslAuthorizationCheck cslCheck) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.expressionBehavior = expressionBehavior;
    this.validator = validator;
    this.cslCheck = cslCheck;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<ExpressionRecord> command) {
    validator
        .validate(command)
        .map(this::applyResolvedTenant)
        .flatMap(validated -> authorize(command, validated))
        .flatMap(this::evaluate)
        .ifRightOrLeft(
            resolvedRecord -> acceptCommand(command, resolvedRecord),
            rejection -> rejectCommand(command, rejection));
  }

  private ValidatedCommand applyResolvedTenant(final ValidatedCommand validated) {
    // Infer the tenant from the resolved instance so downstream auth and variable
    // resolution always operate against the instance's owning tenant. Done here
    // (in the processor) rather than in the validator to keep validation side effect free.
    validated
        .resolvedInstance()
        .map(ResolvedInstance::tenantId)
        .ifPresent(validated.record()::setTenantId);
    return validated;
  }

  private Either<Rejection, ExpressionRecord> evaluate(final ValidatedCommand validated) {
    return expressionBehavior.resolveExpression(validated.expression(), validated.record());
  }

  private Either<Rejection, ValidatedCommand> authorize(
      final TypedRecord<ExpressionRecord> command, final ValidatedCommand validated) {
    final var tenantId = validated.record().getTenantId();

    return checkCanEvaluateExpression(command, tenantId)
        .flatMap(__ -> checkCanReadInstanceIfScoped(command, validated, tenantId))
        .map(__ -> validated);
  }

  private Either<Rejection, ExpressionRecord> checkCanEvaluateExpression(
      final TypedRecord<ExpressionRecord> command, final String tenantId) {
    return cslCheck
        .check(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(
                            AuthzModelMapper.fromProtocol(AuthorizationResourceType.EXPRESSION))
                        .permissionType(AuthzModelMapper.fromProtocol(PermissionType.EVALUATE))
                        .resourceId(AuthorizationScope.WILDCARD_CHAR)),
            command.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.EVALUATE, AuthorizationResourceType.EXPRESSION),
            AuthorizationRejectionMapper::toBareRejection)
        .flatMap(
            v ->
                checkTenantIfPresent(
                    command,
                    tenantId,
                    v,
                    PermissionType.EVALUATE,
                    AuthorizationResourceType.EXPRESSION));
  }

  private Either<Rejection, ExpressionRecord> checkCanReadInstanceIfScoped(
      final TypedRecord<ExpressionRecord> command,
      final ValidatedCommand validated,
      final String tenantId) {
    return validated
        .resolvedInstance()
        .map(instance -> checkCanReadProcessInstance(command, tenantId, instance.bpmnProcessId()))
        .orElseGet(() -> Either.right(command.getValue()));
  }

  private Either<Rejection, ExpressionRecord> checkCanReadProcessInstance(
      final TypedRecord<ExpressionRecord> command,
      final String tenantId,
      final String bpmnProcessId) {
    return cslCheck
        .check(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(
                            AuthzModelMapper.fromProtocol(
                                AuthorizationResourceType.PROCESS_DEFINITION))
                        .permissionType(
                            AuthzModelMapper.fromProtocol(PermissionType.READ_PROCESS_INSTANCE))
                        .resourceId(bpmnProcessId)),
            command.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.READ_PROCESS_INSTANCE, AuthorizationResourceType.PROCESS_DEFINITION))
        .flatMap(
            v ->
                checkTenantIfPresent(
                    command,
                    tenantId,
                    v,
                    PermissionType.READ_PROCESS_INSTANCE,
                    AuthorizationResourceType.PROCESS_DEFINITION));
  }

  private Either<Rejection, ExpressionRecord> checkTenantIfPresent(
      final TypedRecord<ExpressionRecord> command,
      final String tenantId,
      final ExpressionRecord value,
      final PermissionType permissionType,
      final AuthorizationResourceType resourceType) {
    if (tenantId.isBlank()) {
      return Either.right(value);
    }
    return cslCheck.checkTenant(
        command,
        tenantId,
        value,
        new Rejection(
            RejectionType.NOT_FOUND,
            ERROR_MESSAGE_NOT_FOUND_FOR_TENANT.formatted(permissionType, resourceType, tenantId)));
  }

  private void rejectCommand(
      final TypedRecord<ExpressionRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }

  private void acceptCommand(
      final TypedRecord<ExpressionRecord> command, final ExpressionRecord resolvedValue) {
    final var key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, ExpressionIntent.EVALUATED, resolvedValue);
    responseWriter.writeAcceptedResponseOnCommand(
        key, ExpressionIntent.EVALUATED, resolvedValue, command);
  }
}
