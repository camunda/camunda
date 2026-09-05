/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.runtimevariables;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.runtimevariables.RuntimeVariablesRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RuntimeVariablesIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.RuntimeVariableScope;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public final class RuntimeVariablesFetchProcessor
    implements TypedRecordProcessor<RuntimeVariablesRecord> {

  private static final String ERROR_MESSAGE_SCOPE_NOT_FOUND = "No scope found with key '%d'";
  private static final String ERROR_MESSAGE_NOT_FOUND_FOR_TENANT = "No scope found with key '%d'";
  private static final String ERROR_MESSAGE_TOO_LARGE =
      "The requested runtime variable document exceeds the configured maximum message size.";

  private final ElementInstanceState elementInstanceState;
  private final VariableState variableState;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;

  public RuntimeVariablesFetchProcessor(
      final ElementInstanceState elementInstanceState,
      final VariableState variableState,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final Writers writers) {
    this.elementInstanceState = elementInstanceState;
    this.variableState = variableState;
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<RuntimeVariablesRecord> command) {
    resolveInstance(command.getValue().getScopeKey())
        .flatMap(instance -> authorize(command, instance))
        .map(instance -> fetchVariables(command.getValue(), instance))
        .ifRightOrLeft(
            fetched -> acceptCommand(command, fetched),
            rejection -> rejectCommand(command, rejection));
  }

  private Either<Rejection, ElementInstance> resolveInstance(final long scopeKey) {
    final var instance = elementInstanceState.getInstance(scopeKey);
    return instance == null
        ? Either.left(
            new Rejection(
                RejectionType.NOT_FOUND, ERROR_MESSAGE_SCOPE_NOT_FOUND.formatted(scopeKey)))
        : Either.right(instance);
  }

  private Either<Rejection, ElementInstance> authorize(
      final TypedRecord<RuntimeVariablesRecord> command, final ElementInstance instance) {
    final var value = instance.getValue();
    final var tenantId = value.getTenantId();
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
                        .resourceId(value.getBpmnProcessId())),
            instance,
            AuthorizationRejectionMapper.forbidden(
                PermissionType.READ_PROCESS_INSTANCE, AuthorizationResourceType.PROCESS_DEFINITION),
            AuthorizationRejectionMapper::toBareRejection)
        .flatMap(
            authorized ->
                tenantCheck.checkTenant(
                    command,
                    tenantId,
                    authorized,
                    new Rejection(
                        RejectionType.NOT_FOUND,
                        ERROR_MESSAGE_NOT_FOUND_FOR_TENANT.formatted(
                            command.getValue().getScopeKey()))));
  }

  private RuntimeVariablesRecord fetchVariables(
      final RuntimeVariablesRecord request, final ElementInstance instance) {
    final var scopeKey = request.getScopeKey();
    final java.util.function.IntPredicate canWriteDocument =
        length ->
            stateWriter.canWriteEventOfLength(
                length + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER);
    final var variables =
        request.getScope() == RuntimeVariableScope.LOCAL
            ? variableState.getVariablesLocalAsDocument(scopeKey, canWriteDocument)
            : variableState.getVariablesAsDocument(scopeKey, canWriteDocument);
    if (variables.isEmpty()) {
      throw new RuntimeVariablesTooLargeException();
    }
    return request
        .setTenantId(instance.getValue().getTenantId())
        .setVariables(variables.orElseThrow());
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<RuntimeVariablesRecord> command, final Throwable error) {
    if (error instanceof RuntimeVariablesTooLargeException) {
      rejectCommand(
          command, new Rejection(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_TOO_LARGE));
      return ProcessingError.EXPECTED_ERROR;
    }
    return ProcessingError.UNEXPECTED_ERROR;
  }

  private void acceptCommand(
      final TypedRecord<RuntimeVariablesRecord> command, final RuntimeVariablesRecord fetched) {
    responseWriter.writeAcceptedResponseOnCommand(
        fetched.getScopeKey(), RuntimeVariablesIntent.FETCHED, fetched, command);
  }

  private void rejectCommand(
      final TypedRecord<RuntimeVariablesRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }

  private static final class RuntimeVariablesTooLargeException extends RuntimeException {}
}
