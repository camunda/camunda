/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.TaskListener;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.UserTaskRecordRequestMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;

@ExcludeAuthorizationCheck
public class UserTaskProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private static final String USER_TASK_COMPLETION_REJECTION =
      "Completion of the User Task with key '%d' was rejected by Task Listener";

  private final UserTaskCommandProcessors commandProcessors;
  private final ProcessState processState;
  private final MutableUserTaskState userTaskState;
  private final ElementInstanceState elementInstanceState;

  private final BpmnJobBehavior jobBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;

  public UserTaskProcessor(
      final ProcessingState state,
      final MutableUserTaskState userTaskState,
      final KeyGenerator keyGenerator,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    commandProcessors =
        new UserTaskCommandProcessors(
            state, keyGenerator, bpmnBehaviors, writers, authCheckBehavior);
    processState = state.getProcessState();
    this.userTaskState = userTaskState;
    elementInstanceState = state.getElementInstanceState();

    jobBehavior = bpmnBehaviors.jobBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();

    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> command) {
    final UserTaskIntent intent = (UserTaskIntent) command.getIntent();
    switch (intent) {
      case ASSIGN, CLAIM, COMPLETE, UPDATE -> processOperationCommand(command, intent);
      case COMPLETE_TASK_LISTENER -> processCompleteTaskListener(command);
      case DENY_TASK_LISTENER -> processDenyTaskListener(command);
      default -> throw new UnsupportedOperationException("Unexpected user task intent: " + intent);
    }
  }

  private void processCompleteTaskListener(final TypedRecord<UserTaskRecord> command) {
    final var lifecycleState = userTaskState.getLifecycleState(command.getKey());
    final var listenerEventType = mapLifecycleStateToEventType(lifecycleState);
    final var persistedRecord = userTaskState.getUserTask(command.getKey());
    final var userTaskElement = getUserTaskElement(persistedRecord);
    final var userTaskElementInstance = getUserTaskElementInstance(persistedRecord);
    final var context = buildContext(userTaskElementInstance);

    findNextTaskListener(listenerEventType, userTaskElement, userTaskElementInstance)
        .ifPresentOrElse(
            listener -> createTaskListenerJob(listener, context, persistedRecord),
            () -> finalizeCommand(command, lifecycleState, persistedRecord));
  }

  private void finalizeCommand(
      final TypedRecord<UserTaskRecord> command,
      final LifecycleState lifecycleState,
      final UserTaskRecord userTaskRecord) {
    final var commandProcessor = determineProcessorFromUserTaskLifecycleState(lifecycleState);
    commandProcessor.onFinalizeCommand(command, userTaskRecord);
  }

  private void processDenyTaskListener(final TypedRecord<UserTaskRecord> command) {
    final var lifecycleState = userTaskState.getLifecycleState(command.getKey());
    final var persistedRecord = userTaskState.getUserTask(command.getKey());

    // Improvement: introduce switch case based on lifecycle stages in the future
    if (lifecycleState == LifecycleState.COMPLETING) {
      writeRejectionForCommand(command, persistedRecord, UserTaskIntent.COMPLETION_DENIED);
    } else {
      throw new IllegalArgumentException(
          "Expected to reject operation for user task: '%d', but operation could not be determined from the task's current lifecycle state: '%s'"
              .formatted(command.getValue().getUserTaskKey(), lifecycleState));
    }
  }

  private void processOperationCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskIntent intent) {
    final var commandProcessor = commandProcessors.getCommandProcessor(intent);
    commandProcessor
        .validateCommand(command)
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
      /*
       * Store the original command's metadata (requestId, requestStreamId) before creating
       * the first task listener job. This metadata will be used later to finalize the original
       * command after all task listeners have been processed, ensuring that the engine can respond
       * appropriately to the original command request.
       *
       * Note:
       * Typically, persistence logic should be handled via `*Applier` classes. However, since
       * this involves request-related data, so in the case of command reconstruction,
       * we will have new request values anyway, so persisting these data here is acceptable.
       * A similar approach has been used in `ProcessInstanceCreationCreateWithResultProcessor`.
       */
      storeUserTaskRecordRequestMetadata(command);

      final var listener = userTaskElement.getTaskListeners(eventType).getFirst();
      final var userTaskElementInstance = getUserTaskElementInstance(persistedRecord);
      final var context = buildContext(userTaskElementInstance);
      createTaskListenerJob(listener, context, persistedRecord);
    } else {
      processor.onFinalizeCommand(command, persistedRecord);
    }
  }

  void storeUserTaskRecordRequestMetadata(final TypedRecord<UserTaskRecord> command) {
    final var metadata =
        new UserTaskRecordRequestMetadata()
            .setIntent((UserTaskIntent) command.getIntent())
            .setRequestId(command.getRequestId())
            .setRequestStreamId(command.getRequestStreamId());
    userTaskState.storeRecordRequestMetadata(command.getValue().getUserTaskKey(), metadata);
  }

  private void handleCommandRejection(
      final TypedRecord<UserTaskRecord> command,
      final RejectionType rejectionType,
      final String rejectionReason) {
    rejectionWriter.appendRejection(command, rejectionType, rejectionReason);
    responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
  }

  private Optional<TaskListener> findNextTaskListener(
      final ZeebeTaskListenerEventType eventType,
      final ExecutableUserTask userTask,
      final ElementInstance userTaskElementInstance) {
    final var listeners = userTask.getTaskListeners(eventType);
    final int currentListenerIndex = userTaskElementInstance.getTaskListenerIndex(eventType);
    return listeners.stream().skip(currentListenerIndex).findFirst();
  }

  private void createTaskListenerJob(
      final TaskListener listener,
      final BpmnElementContext context,
      final UserTaskRecord taskRecordValue) {
    jobBehavior
        .evaluateTaskListenerJobExpressions(
            listener.getJobWorkerProperties(), context, taskRecordValue)
        .thenDo(
            listenerJobProperties ->
                jobBehavior.createNewTaskListenerJob(
                    context, listenerJobProperties, listener, taskRecordValue))
        .ifLeft(failure -> incidentBehavior.createIncident(failure, context));
  }

  private void writeRejectionForCommand(
      final TypedRecord<UserTaskRecord> command,
      final UserTaskRecord persistedRecord,
      final UserTaskIntent intent) {

    final var recordRequestMetadata =
        userTaskState.findRecordRequestMetadata(persistedRecord.getUserTaskKey());

    stateWriter.appendFollowUpEvent(persistedRecord.getUserTaskKey(), intent, persistedRecord);

    recordRequestMetadata.ifPresent(
        metadata -> {
          responseWriter.writeRejection(
              command.getKey(),
              UserTaskIntent.COMPLETE,
              command.getValue(),
              command.getValueType(),
              RejectionType.INVALID_STATE,
              USER_TASK_COMPLETION_REJECTION.formatted(persistedRecord.getUserTaskKey()),
              metadata.getRequestId(),
              metadata.getRequestStreamId());
        });
  }

  private ExecutableUserTask getUserTaskElement(final UserTaskRecord userTaskRecord) {
    return processState.getFlowElement(
        userTaskRecord.getProcessDefinitionKey(),
        userTaskRecord.getTenantId(),
        userTaskRecord.getElementIdBuffer(),
        ExecutableUserTask.class);
  }

  private ZeebeTaskListenerEventType mapIntentToEventType(final UserTaskIntent intent) {
    return switch (intent) {
      case ASSIGN, CLAIM -> ZeebeTaskListenerEventType.assignment;
      case UPDATE -> ZeebeTaskListenerEventType.update;
      case COMPLETE -> ZeebeTaskListenerEventType.complete;
      default ->
          throw new IllegalArgumentException("Unexpected user task intent: '%s'".formatted(intent));
    };
  }

  private ZeebeTaskListenerEventType mapLifecycleStateToEventType(
      final LifecycleState lifecycleState) {
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

  private UserTaskCommandProcessor determineProcessorFromUserTaskLifecycleState(
      final LifecycleState lifecycleState) {

    final var userTaskIntent =
        switch (lifecycleState) {
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

    return commandProcessors.getCommandProcessor(userTaskIntent);
  }

  private ElementInstance getUserTaskElementInstance(final UserTaskRecord userTaskRecord) {
    final var elementInstanceKey = userTaskRecord.getElementInstanceKey();
    return elementInstanceState.getInstance(elementInstanceKey);
  }

  private BpmnElementContext buildContext(final ElementInstance elementInstance) {
    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstance.getValue(), elementInstance.getState());
    return context;
  }
}
