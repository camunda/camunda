/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE;

import io.camunda.zeebe.auth.impl.TenantAuthorizationCheckerImpl;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.msgpack.spec.MsgpackReaderException;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.DirectBuffer;

public final class VariableDocumentUpdateProcessor
    implements TypedRecordProcessor<VariableDocumentRecord> {

  private static final String ERROR_MESSAGE_SCOPE_NOT_FOUND =
      "Expected to update variables for element with key '%d', but no such element was found";

  private final ElementInstanceState elementInstanceState;
  private final KeyGenerator keyGenerator;
  private final VariableBehavior variableBehavior;
  private final Writers writers;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public VariableDocumentUpdateProcessor(
      final ElementInstanceState elementInstanceState,
      final KeyGenerator keyGenerator,
      final VariableBehavior variableBehavior,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.elementInstanceState = elementInstanceState;
    this.keyGenerator = keyGenerator;
    this.variableBehavior = variableBehavior;
    this.writers = writers;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<VariableDocumentRecord> record) {
    final VariableDocumentRecord value = record.getValue();

    final ElementInstance scope = elementInstanceState.getInstance(value.getScopeKey());
    if (scope == null || scope.isTerminating() || scope.isInFinalState()) {
      final String reason = String.format(ERROR_MESSAGE_SCOPE_NOT_FOUND, value.getScopeKey());
      writers.rejection().appendRejection(record, RejectionType.NOT_FOUND, reason);
      writers.response().writeRejectionOnCommand(record, RejectionType.NOT_FOUND, reason);
      return;
    }

    final var authRequest =
        new AuthorizationRequest(
                record, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE)
            .addResourceId(scope.getValue().getBpmnProcessId());
    if (!authCheckBehavior.isAuthorized(authRequest)) {
      final var reason =
          UNAUTHORIZED_ERROR_MESSAGE.formatted(
              authRequest.getPermissionType(), authRequest.getResourceType());
      writers.rejection().appendRejection(record, RejectionType.UNAUTHORIZED, reason);
      writers.response().writeRejectionOnCommand(record, RejectionType.UNAUTHORIZED, reason);
      return;
    }

    if (!TenantAuthorizationCheckerImpl.fromAuthorizationMap(record.getAuthorizations())
        .isAuthorized(scope.getValue().getTenantId())) {
      final String reason = String.format(ERROR_MESSAGE_SCOPE_NOT_FOUND, value.getScopeKey());
      writers.rejection().appendRejection(record, RejectionType.NOT_FOUND, reason);
      writers.response().writeRejectionOnCommand(record, RejectionType.NOT_FOUND, reason);
      return;
    }

    final long processDefinitionKey = scope.getValue().getProcessDefinitionKey();
    final long processInstanceKey = scope.getValue().getProcessInstanceKey();
    final DirectBuffer bpmnProcessId = scope.getValue().getBpmnProcessIdBuffer();
    final String tenantId = scope.getValue().getTenantId();
    try {
      if (value.getUpdateSemantics() == VariableDocumentUpdateSemantic.LOCAL) {
        variableBehavior.mergeLocalDocument(
            scope.getKey(),
            processDefinitionKey,
            processInstanceKey,
            bpmnProcessId,
            tenantId,
            value.getVariablesBuffer());
      } else {
        variableBehavior.mergeDocument(
            scope.getKey(),
            processDefinitionKey,
            processInstanceKey,
            bpmnProcessId,
            tenantId,
            value.getVariablesBuffer());
      }
    } catch (final MsgpackReaderException e) {
      final String reason =
          String.format(
              "Expected document to be valid msgpack, but it could not be read: '%s'",
              e.getMessage());
      writers.rejection().appendRejection(record, RejectionType.INVALID_ARGUMENT, reason);
      writers.response().writeRejectionOnCommand(record, RejectionType.INVALID_ARGUMENT, reason);
      return;
    }

    final long key = keyGenerator.nextKey();

    writers.state().appendFollowUpEvent(key, VariableDocumentIntent.UPDATED, value);
    writers.response().writeEventOnCommand(key, VariableDocumentIntent.UPDATED, value, record);
  }
}
