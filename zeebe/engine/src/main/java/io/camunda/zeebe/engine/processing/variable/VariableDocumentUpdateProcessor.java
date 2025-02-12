/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.UserTaskRecordRequestMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.msgpack.spec.MsgpackReaderException;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
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
  private final ProcessState processState;
  private final MutableUserTaskState userTaskState;
  private final BpmnJobBehavior jobBehavior;

  public VariableDocumentUpdateProcessor(
      final ElementInstanceState elementInstanceState,
      final KeyGenerator keyGenerator,
      final VariableBehavior variableBehavior,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final ProcessState processState,
      final MutableUserTaskState userTaskState,
      final BpmnJobBehavior jobBehavior) {
    this.elementInstanceState = elementInstanceState;
    this.keyGenerator = keyGenerator;
    this.variableBehavior = variableBehavior;
    this.writers = writers;
    this.authCheckBehavior = authCheckBehavior;
    this.processState = processState;
    this.userTaskState = userTaskState;
    this.jobBehavior = jobBehavior;
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

    final String tenantId = scope.getValue().getTenantId();
    final var authRequest =
        new AuthorizationRequest(
                record,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.UPDATE_PROCESS_INSTANCE,
                scope.getValue().getTenantId())
            .addResourceId(scope.getValue().getBpmnProcessId());
    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      final String errorMessage =
          RejectionType.NOT_FOUND.equals(rejection.type())
              ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                  "update variables for element",
                  scope.getValue().getProcessInstanceKey(),
                  "such element")
              : rejection.reason();
      writers.rejection().appendRejection(record, rejection.type(), errorMessage);
      writers.response().writeRejectionOnCommand(record, rejection.type(), errorMessage);
      return;
    }

    final var shouldDryRun = false;
    if (scope.getValue().getBpmnElementType() == BpmnElementType.USER_TASK
        && scope.getUserTaskKey() > -1L) {

      // From now on, SET VARIABLES on CAMUNDA USER TASK flows through the User Task UPDATE logic

      // We need to store the request id and request stream id
      // TODO: Can we store it on the command directly (propagate it to the follow-up command)?
      // This would mean the UserTaskProcessor can take care of storing it for future use
      // All we need to watch out with is how we end up responding to the request
      // Can we figure out in the UserTaskProcessor what command we need to respond to?
      // Alternatively, we can store it here, highlighting the unique intent (see
      // UserTaskProcessor.storeUserTaskRecordRequestMetadata), and ensure we don't overwrite it
      // in the UserTaskProcessor (easy just don't provide the request data on the command)
      final var metadata =
          new UserTaskRecordRequestMetadata()
              .setIntent((UserTaskIntent) record.getIntent()) // todo: this cast will fail
              .setRequestId(record.getRequestId())
              .setRequestStreamId(record.getRequestStreamId());
      userTaskState.storeRecordRequestMetadata(scope.getUserTaskKey(), metadata);

      final long key = keyGenerator.nextKey();
      writers.state().appendFollowUpEvent(key, VariableDocumentIntent.UPDATING, value);

      final var userTaskRecord = userTaskState.getUserTask(scope.getUserTaskKey());
      writers
          .command()
          .appendFollowUpCommand(
              scope.getUserTaskKey(),
              UserTaskIntent.UPDATE,
              userTaskRecord
                  .resetChangedAttributes()
                  .setVariables(value.getVariablesBuffer())
                  .setVariableChanged());

      return;
    }

    var hasChangedVariables = false;
    final long processDefinitionKey = scope.getValue().getProcessDefinitionKey();
    final long processInstanceKey = scope.getValue().getProcessInstanceKey();
    final DirectBuffer bpmnProcessId = scope.getValue().getBpmnProcessIdBuffer();
    try {
      if (value.getUpdateSemantics() == VariableDocumentUpdateSemantic.LOCAL) {
        hasChangedVariables =
            variableBehavior.mergeLocalDocument(
                scope.getKey(),
                processDefinitionKey,
                processInstanceKey,
                bpmnProcessId,
                tenantId,
                value.getVariablesBuffer(),
                shouldDryRun);
      } else {
        hasChangedVariables =
            variableBehavior.mergeDocument(
                scope.getKey(),
                processDefinitionKey,
                processInstanceKey,
                bpmnProcessId,
                tenantId,
                value.getVariablesBuffer(),
                shouldDryRun);
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

  private BpmnElementContext buildContext(final ElementInstance elementInstance) {
    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstance.getValue(), elementInstance.getState());
    return context;
  }
}
