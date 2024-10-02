/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.bpmn.ProcessElementProperties;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.TaskListener;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;

public class UserTaskProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private final UserTaskCommandProcessors commandProcessors;
  private final ProcessState processState;
  private final UserTaskState userTaskState;
  private final ElementInstanceState elementInstanceState;
  private final EventScopeInstanceState eventScopeInstanceState;

  private final BpmnJobBehavior jobBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final VariableBehavior variableBehavior;
  private final EventTriggerBehavior eventTriggerBehavior;

  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public UserTaskProcessor(
      final MutableProcessingState state,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers) {
    this.commandProcessors = new UserTaskCommandProcessors(state, bpmnBehaviors, writers);
    this.processState = state.getProcessState();
    this.userTaskState = state.getUserTaskState();
    this.elementInstanceState = state.getElementInstanceState();
    this.eventScopeInstanceState = state.getEventScopeInstanceState();

    this.jobBehavior = bpmnBehaviors.jobBehavior();
    this.incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.variableBehavior = bpmnBehaviors.variableBehavior();
    this.eventTriggerBehavior = bpmnBehaviors.eventTriggerBehavior();

    this.rejectionWriter = writers.rejection();
    this.responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> command) {
    final UserTaskIntent intent = (UserTaskIntent) command.getIntent();
    if (intent == UserTaskIntent.COMPLETE_TASK_LISTENER) {
      processCompleteTaskListener(command);
    } else {
      processOperationCommand(command, intent);
    }
  }

  private void processCompleteTaskListener(final TypedRecord<UserTaskRecord> command) {
    final var lifecycleState = userTaskState.getLifecycleState(command.getKey());
    final var userTaskIntent = mapLifecycleStateToIntent(lifecycleState);
    final var commandProcessor = commandProcessors.getCommandProcessor(userTaskIntent);

    final var persistedRecord = command.getValue();
    final var userTaskElementInstanceKey = persistedRecord.getElementInstanceKey();
    final var userTaskElementInstance =
        elementInstanceState.getInstance(userTaskElementInstanceKey);

    final var eventType = mapLifecycleStateToEventType(lifecycleState);
    final var userTaskElement = getUserTaskElement(persistedRecord);
    final var taskListeners = userTaskElement.getTaskListeners(eventType);
    final int taskListenerIndex = userTaskElementInstance.getTaskListenerIndex(eventType);
    final var elementProperties = ProcessElementProperties.from(persistedRecord);

    mergeVariablesOfTaskListener(elementProperties);
    findNextTaskListener(taskListeners, taskListenerIndex)
        .ifPresentOrElse(
            listener -> createTaskListenerJob(listener, elementProperties, persistedRecord),
            () -> commandProcessor.onFinalizeCommand(command, persistedRecord));
  }

  private void processOperationCommand(
      final TypedRecord<UserTaskRecord> command, UserTaskIntent intent) {
    final var commandProcessor = commandProcessors.getCommandProcessor(intent);
    commandProcessor
        .check(command)
        .ifRightOrLeft(
            persistedRecord ->
                handleCommandProcessing(commandProcessor, command, persistedRecord, intent),
            violation ->
                handleCommandRejection(command, violation.getLeft(), violation.getRight()));
  }

  private void handleCommandProcessing(
      final UserTaskCommandProcessor processor,
      final TypedRecord<UserTaskRecord> command,
      final UserTaskRecord persistedRecord,
      final UserTaskIntent intent) {

    processor.onCommand(command, persistedRecord);

    final var userTaskElement = getUserTaskElement(persistedRecord);
    final var eventType = mapIntentToEventType(intent);

    if (userTaskElement.hasTaskListeners(eventType)) {
      final var listener = userTaskElement.getTaskListeners(eventType).getFirst();
      final var elementProperties = ProcessElementProperties.from(persistedRecord);
      createTaskListenerJob(listener, elementProperties, persistedRecord);
    } else {
      processor.onFinalizeCommand(command, persistedRecord);
    }
  }

  private void handleCommandRejection(
      final TypedRecord<UserTaskRecord> command,
      final RejectionType rejectionType,
      final String rejectionReason) {
    rejectionWriter.appendRejection(command, rejectionType, rejectionReason);
    responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
  }

  private Optional<TaskListener> findNextTaskListener(
      final List<TaskListener> listeners, final int nextListenerIndex) {
    return listeners.stream().skip(nextListenerIndex).findFirst();
  }

  private void createTaskListenerJob(
      final TaskListener listener,
      final ProcessElementProperties elementProps,
      final UserTaskRecord taskRecordValue) {
    jobBehavior
        .evaluateTaskListenerJobExpressions(
            listener.getJobWorkerProperties(), elementProps, taskRecordValue)
        .thenDo(
            listenerJobProperties ->
                jobBehavior.createNewTaskListenerJob(
                    elementProps, listenerJobProperties, listener, taskRecordValue))
        .ifLeft(failure -> incidentBehavior.createIncident(failure, elementProps));
  }

  private ExecutableUserTask getUserTaskElement(final UserTaskRecord userTaskRecord) {
    return processState.getFlowElement(
        userTaskRecord.getProcessDefinitionKey(),
        userTaskRecord.getTenantId(),
        userTaskRecord.getElementIdBuffer(),
        ExecutableUserTask.class);
  }

  private ZeebeTaskListenerEventType mapIntentToEventType(UserTaskIntent intent) {
    return switch (intent) {
      case ASSIGN, CLAIM -> ZeebeTaskListenerEventType.assignment;
      case UPDATE -> ZeebeTaskListenerEventType.update;
      case COMPLETE -> ZeebeTaskListenerEventType.complete;
      default ->
          throw new IllegalArgumentException("Unexpected user task intent: '%s'".formatted(intent));
    };
  }

  private ZeebeTaskListenerEventType mapLifecycleStateToEventType(LifecycleState lifecycleState) {
    return switch (lifecycleState) {
      case CREATING -> ZeebeTaskListenerEventType.create;
      case ASSIGNING -> ZeebeTaskListenerEventType.assignment;
      case UPDATING -> ZeebeTaskListenerEventType.update;
      case COMPLETING -> ZeebeTaskListenerEventType.complete;
      case CANCELING -> ZeebeTaskListenerEventType.cancel;
      default ->
          throw new IllegalArgumentException(
              "Unexpected user task lifecycle state: '%s'".formatted(lifecycleState));
    };
  }

  private UserTaskIntent mapLifecycleStateToIntent(LifecycleState lifecycleState) {
    return switch (lifecycleState) {
      case ASSIGNING -> UserTaskIntent.ASSIGN;
      case UPDATING -> UserTaskIntent.UPDATE;
      case COMPLETING -> UserTaskIntent.COMPLETE;
      case CREATING, CANCELING ->
          throw new UnsupportedOperationException(
              "Conversion from '%s' user task lifecycle state to a user task command is not yet supported"
                  .formatted(lifecycleState));
      default ->
          throw new IllegalArgumentException(
              "Unexpected user task lifecycle state: '%s'".formatted(lifecycleState));
    };
  }

  private void mergeVariablesOfTaskListener(final ProcessElementProperties elementProperties) {
    Optional.ofNullable(
            eventScopeInstanceState.peekEventTrigger(elementProperties.getElementInstanceKey()))
        .ifPresent(
            eventTrigger -> {
              if (eventTrigger.getVariables().capacity() > 0) {
                final long scopeKey = elementProperties.getElementInstanceKey();

                variableBehavior.mergeLocalDocument(
                    scopeKey,
                    elementProperties.getProcessDefinitionKey(),
                    elementProperties.getProcessInstanceKey(),
                    BufferUtil.wrapString(elementProperties.getBpmnProcessId()),
                    elementProperties.getTenantId(),
                    eventTrigger.getVariables());
              }

              eventTriggerBehavior.processEventTriggered(
                  eventTrigger.getEventKey(),
                  elementProperties.getProcessDefinitionKey(),
                  eventTrigger.getProcessInstanceKey(),
                  elementProperties.getTenantId(),
                  elementProperties.getElementInstanceKey(),
                  eventTrigger.getElementId());
            });
  }
}
