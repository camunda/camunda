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
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.UserTaskTransitionTriggerRequestMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.msgpack.spec.MsgpackReaderException;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
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

  private static final String INVALID_USER_TASK_STATE_MESSAGE =
      "Expected to trigger update transition for user task with key '%d', but it is in state '%s'";

  private final ElementInstanceState elementInstanceState;
  private final MutableUserTaskState userTaskState;
  private final ProcessState processState;
  private final KeyGenerator keyGenerator;
  private final VariableBehavior variableBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final Writers writers;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public VariableDocumentUpdateProcessor(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final MutableUserTaskState userTaskState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.elementInstanceState = processingState.getElementInstanceState();
    this.userTaskState = userTaskState;
    this.processState = processingState.getProcessState();
    this.keyGenerator = keyGenerator;
    this.variableBehavior = bpmnBehaviors.variableBehavior();
    this.jobBehavior = bpmnBehaviors.jobBehavior();
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

    final String tenantId = scope.getValue().getTenantId();

    if (isCamundaUserTask(scope)) {
      final long userTaskKey = scope.getUserTaskKey();
      final var lifecycleState = userTaskState.getLifecycleState(userTaskKey);
      if (lifecycleState != LifecycleState.CREATED) {
        final var reason = INVALID_USER_TASK_STATE_MESSAGE.formatted(userTaskKey, lifecycleState);
        writers.rejection().appendRejection(record, RejectionType.INVALID_STATE, reason);
        writers.response().writeRejectionOnCommand(record, RejectionType.INVALID_STATE, reason);
        return;
      }

      final long key = keyGenerator.nextKey();
      writers.state().appendFollowUpEvent(key, VariableDocumentIntent.UPDATING, value);

      final var userTaskRecord = userTaskState.getUserTask(userTaskKey);
      userTaskRecord.setVariables(value.getVariablesBuffer()).setVariablesChanged();
      writers.state().appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATING, userTaskRecord);

      final var userTaskElement =
          processState.getFlowElement(
              userTaskRecord.getProcessDefinitionKey(),
              tenantId,
              userTaskRecord.getElementIdBuffer(),
              ExecutableUserTask.class);

      if (userTaskElement.hasTaskListeners(ZeebeTaskListenerEventType.updating)) {
        storeRecordRequestMetadata(userTaskKey, record);

        final var listener =
            userTaskElement.getTaskListeners(ZeebeTaskListenerEventType.updating).getFirst();
        jobBehavior.createNewTaskListenerJob(
            buildContext(scope), userTaskRecord, listener, userTaskRecord.getChangedAttributes());
        return;
      }

      variableBehavior.mergeLocalDocument(
          userTaskRecord.getElementInstanceKey(),
          userTaskRecord.getProcessDefinitionKey(),
          userTaskRecord.getProcessInstanceKey(),
          userTaskRecord.getBpmnProcessIdBuffer(),
          userTaskRecord.getTenantId(),
          value.getVariablesBuffer());

      writers
          .state()
          .appendFollowUpEvent(scope.getUserTaskKey(), UserTaskIntent.UPDATED, userTaskRecord);

      writers.state().appendFollowUpEvent(key, VariableDocumentIntent.UPDATED, value);
      writers.response().writeEventOnCommand(key, VariableDocumentIntent.UPDATED, value, record);
      return;
    }

    final long processDefinitionKey = scope.getValue().getProcessDefinitionKey();
    final long processInstanceKey = scope.getValue().getProcessInstanceKey();
    final DirectBuffer bpmnProcessId = scope.getValue().getBpmnProcessIdBuffer();
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

  private static boolean isCamundaUserTask(ElementInstance elementInstance) {
    return elementInstance.getValue().getBpmnElementType() == BpmnElementType.USER_TASK
        && elementInstance.getUserTaskKey() > -1L;
  }

  private static BpmnElementContext buildContext(final ElementInstance elementInstance) {
    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstance.getValue(), elementInstance.getState());
    return context;
  }

  void storeRecordRequestMetadata(
      final long userTaskKey, final TypedRecord<VariableDocumentRecord> command) {
    if (!command.hasRequestMetadata()) {
      return;
    }

    final var metadata =
        new UserTaskTransitionTriggerRequestMetadata()
            .setIntent(command.getIntent())
            .setTriggerType(ValueType.VARIABLE_DOCUMENT)
            .setRequestId(command.getRequestId())
            .setRequestStreamId(command.getRequestStreamId());
    userTaskState.storeRecordRequestMetadata(userTaskKey, metadata);
  }
}
