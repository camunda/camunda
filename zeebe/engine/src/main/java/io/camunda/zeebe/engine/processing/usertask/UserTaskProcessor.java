/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.TaskListener;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.incident.RetryTypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;
import java.util.Set;

@ExcludeAuthorizationCheck
public class UserTaskProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private static final String USER_TASK_COMPLETION_REJECTION =
      "Completion of the User Task with key '%d' was denied by Task Listener. Reason to deny: '%s'";

  private static final String USER_TASK_ASSIGNMENT_REJECTION =
      "Assignment of the User Task with key '%d' was denied by Task Listener. Reason to deny: '%s'";

  private static final String USER_TASK_UPDATE_REJECTION =
      "Update of the User Task with key '%d' was denied by Task Listener. Reason to deny: '%s'";

  private static final String USER_TASK_VARIABLE_UPDATE_REJECTION =
      "Variable update for user task instance with key '%d' was denied by Task Listener. Reason to deny: '%s'";

  private final UserTaskCommandProcessors commandProcessors;
  private final ProcessState processState;
  private final MutableUserTaskState userTaskState;
  private final ElementInstanceState elementInstanceState;
  private final VariableState variableState;
  private final AsyncRequestState asyncRequestState;

  private final BpmnJobBehavior jobBehavior;
  private final BpmnUserTaskBehavior userTaskBehavior;

  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;

  public UserTaskProcessor(
      final ProcessingState state,
      final MutableUserTaskState userTaskState,
      final KeyGenerator keyGenerator,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    commandProcessors =
        new UserTaskCommandProcessors(
            state, keyGenerator, bpmnBehaviors, writers, asyncRequestBehavior, authCheckBehavior);
    processState = state.getProcessState();
    this.userTaskState = userTaskState;
    elementInstanceState = state.getElementInstanceState();
    variableState = state.getVariableState();
    asyncRequestState = state.getAsyncRequestState();

    jobBehavior = bpmnBehaviors.jobBehavior();
    userTaskBehavior = bpmnBehaviors.userTaskBehavior();

    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> command) {
    final UserTaskIntent intent = (UserTaskIntent) command.getIntent();
    switch (intent) {
      case CREATE, ASSIGN, CLAIM, UPDATE, COMPLETE, CANCEL ->
          processOperationCommand(command, intent);
      case COMPLETE_TASK_LISTENER -> processCompleteTaskListener(command);
      case DENY_TASK_LISTENER -> processDenyTaskListener(command);
      default -> throw new UnsupportedOperationException("Unexpected user task intent: " + intent);
    }
  }

  private void processCompleteTaskListener(final TypedRecord<UserTaskRecord> command) {
    final var lifecycleState = userTaskState.getLifecycleState(command.getKey());
    final var listenerEventType = mapLifecycleStateToEventType(lifecycleState);
    // we need to copy the intermediate user task record as we have read it from the state, and we
    // will read from the state again later, which in turn would modify this record
    final var intermediateUserTaskRecord =
        userTaskState.getIntermediateState(command.getKey()).getRecord().copy();
    final var userTaskElement = getUserTaskElement(intermediateUserTaskRecord);
    final var userTaskElementInstance = getUserTaskElementInstance(intermediateUserTaskRecord);
    final var context = buildContext(userTaskElementInstance);

    if (command.getValue().hasChangedAttributes()) {
      intermediateUserTaskRecord.wrapChangedAttributesIfValueChanged(command.getValue());

      if (intermediateUserTaskRecord.hasChangedAttributes()) {
        stateWriter.appendFollowUpEvent(
            command.getKey(), UserTaskIntent.CORRECTED, intermediateUserTaskRecord);
      }
    }

    findNextTaskListener(listenerEventType, userTaskElement, userTaskElementInstance)
        .ifPresentOrElse(
            listener -> {
              final var currentUserTask = userTaskState.getUserTask(command.getKey());
              final var changedAttributes =
                  intermediateUserTaskRecord.determineChangedAttributes(currentUserTask);
              jobBehavior.createNewTaskListenerJob(
                  context, intermediateUserTaskRecord, listener, changedAttributes);
            },
            () -> finalizeCommand(command, lifecycleState, intermediateUserTaskRecord));
  }

  private void finalizeCommand(
      final TypedRecord<UserTaskRecord> command,
      final LifecycleState lifecycleState,
      final UserTaskRecord userTaskRecord) {
    final var currentUserTask = userTaskState.getUserTask(command.getKey());
    userTaskRecord.setDiffAsChangedAttributes(currentUserTask);

    final var commandProcessor = determineProcessorFromUserTaskLifecycleState(lifecycleState);
    commandProcessor.onFinalizeCommand(command, userTaskRecord);
  }

  private void processDenyTaskListener(final TypedRecord<UserTaskRecord> command) {
    final var lifecycleState = userTaskState.getLifecycleState(command.getKey());
    final var persistedRecord = userTaskState.getUserTask(command.getKey());

    switch (lifecycleState) {
      case COMPLETING ->
          writeRejectionForCommand(command, persistedRecord, UserTaskIntent.COMPLETION_DENIED);
      case ASSIGNING, CLAIMING ->
          writeRejectionForCommand(command, persistedRecord, UserTaskIntent.ASSIGNMENT_DENIED);
      case UPDATING ->
          writeRejectionForCommand(command, persistedRecord, UserTaskIntent.UPDATE_DENIED);
      default ->
          throw new IllegalArgumentException(
              "Expected to reject operation for user task: '%d', but operation could not be determined from the task's current lifecycle state: '%s'"
                  .formatted(command.getValue().getUserTaskKey(), lifecycleState));
    }
  }

  private void processOperationCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskIntent intent) {
    final var commandProcessor = commandProcessors.getCommandProcessor(intent);

    if (isRetriedCommand(command)) {
      // Skip `validateCommand` and `onCommand` invocations for retried command,
      // as it was already validated during the original execution
      finalizeCommandOrCreateTaskListenerJob(commandProcessor, command, command.getValue(), intent);
    } else {
      commandProcessor
          .validateCommand(command)
          // Create a modifiable copy of the persisted user task record, as `onCommand` may
          // apply the changed attributes from the command on top of persisted instance.
          .map(UserTaskRecord::copy)
          .thenDo(persistedRecord -> commandProcessor.onCommand(command, persistedRecord))
          .ifRightOrLeft(
              persistedRecord ->
                  finalizeCommandOrCreateTaskListenerJob(
                      commandProcessor, command, persistedRecord, intent),
              rejection -> handleCommandRejection(command, rejection));
    }
  }

  private void finalizeCommandOrCreateTaskListenerJob(
      final UserTaskCommandProcessor processor,
      final TypedRecord<UserTaskRecord> command,
      final UserTaskRecord persistedRecord,
      final UserTaskIntent intent) {

    final var userTaskElement = getUserTaskElement(persistedRecord);
    final var eventType = mapIntentToEventType(intent);

    final var firstTaskListener =
        userTaskBehavior
            .getTaskListeners(userTaskElement, persistedRecord.getUserTaskKey(), eventType)
            .stream()
            .findFirst();

    if (firstTaskListener.isPresent()) {
      final var listener = firstTaskListener.get();
      final var userTaskElementInstance = getUserTaskElementInstance(persistedRecord);
      final var context = buildContext(userTaskElementInstance);
      jobBehavior.createNewTaskListenerJob(
          context, persistedRecord, listener, persistedRecord.getChangedAttributes());
    } else {
      processor.onFinalizeCommand(command, persistedRecord);
    }
  }

  private boolean isRetriedCommand(final TypedRecord<UserTaskRecord> command) {
    return command instanceof RetryTypedRecord<UserTaskRecord>;
  }

  private void handleCommandRejection(
      final TypedRecord<UserTaskRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
  }

  private Optional<TaskListener> findNextTaskListener(
      final ZeebeTaskListenerEventType eventType,
      final ExecutableUserTask userTask,
      final ElementInstance userTaskElementInstance) {
    final var listeners =
        userTaskBehavior.getTaskListeners(
            userTask, userTaskElementInstance.getUserTaskKey(), eventType);
    final int currentListenerIndex = userTaskElementInstance.getTaskListenerIndex(eventType);
    return listeners.stream().skip(currentListenerIndex).findFirst();
  }

  private void writeRejectionForCommand(
      final TypedRecord<UserTaskRecord> command,
      final UserTaskRecord persistedRecord,
      final UserTaskIntent intentToWrite) {
    final long userTaskInstanceKey = persistedRecord.getElementInstanceKey();
    persistedRecord.setDeniedReason(command.getValue().getDeniedReason());
    stateWriter.appendFollowUpEvent(
        persistedRecord.getUserTaskKey(), intentToWrite, persistedRecord);

    final var expectedValueTypes = Set.of(ValueType.USER_TASK, ValueType.VARIABLE_DOCUMENT);
    asyncRequestState
        .findAllRequestsByScopeKey(userTaskInstanceKey)
        .filter(request -> expectedValueTypes.contains(request.valueType()))
        // Currently, we assume that at most one async request exists per user task element
        // instance.
        // However, this assumption may need to be revisited if we later support concurrent
        // operations targeting the same user task element.
        .findFirst()
        .ifPresent(
            request -> {
              switch (request.valueType()) {
                case USER_TASK ->
                    responseWriter.writeRejection(
                        command.getKey(),
                        mapDeniedIntentToResponseIntent(intentToWrite),
                        command.getValue(),
                        command.getValueType(),
                        RejectionType.INVALID_STATE,
                        mapDeniedIntentToResponseRejectionReason(
                            intentToWrite,
                            persistedRecord.getUserTaskKey(),
                            command.getValue().getDeniedReason()),
                        request.requestId(),
                        request.requestStreamId());
                case VARIABLE_DOCUMENT ->
                    variableState
                        .findVariableDocumentState(userTaskInstanceKey)
                        .ifPresent(
                            variableDocumentState -> {
                              final long variableDocumentKey = variableDocumentState.getKey();
                              final var variableDocumentRecord = variableDocumentState.getRecord();
                              stateWriter.appendFollowUpEvent(
                                  variableDocumentKey,
                                  VariableDocumentIntent.UPDATE_DENIED,
                                  variableDocumentRecord,
                                  m -> m.operationReference(request.operationReference()));

                              final var deniedReason =
                                  USER_TASK_VARIABLE_UPDATE_REJECTION.formatted(
                                      userTaskInstanceKey, command.getValue().getDeniedReason());
                              responseWriter.writeRejection(
                                  variableDocumentKey,
                                  VariableDocumentIntent.UPDATE,
                                  variableDocumentRecord,
                                  ValueType.VARIABLE_DOCUMENT,
                                  RejectionType.INVALID_STATE,
                                  deniedReason,
                                  request.requestId(),
                                  request.requestStreamId());
                            });
                default ->
                    throw new IllegalArgumentException(
                        "Unexpected value type: '%s', only %s are expected"
                            .formatted(request.valueType(), expectedValueTypes));
              }
              stateWriter.appendFollowUpEvent(
                  request.key(), AsyncRequestIntent.PROCESSED, request.record());
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
      case CREATE -> ZeebeTaskListenerEventType.creating;
      case ASSIGN, CLAIM -> ZeebeTaskListenerEventType.assigning;
      case UPDATE -> ZeebeTaskListenerEventType.updating;
      case COMPLETE -> ZeebeTaskListenerEventType.completing;
      case CANCEL -> ZeebeTaskListenerEventType.canceling;
      default ->
          throw new IllegalArgumentException("Unexpected user task intent: '%s'".formatted(intent));
    };
  }

  private ZeebeTaskListenerEventType mapLifecycleStateToEventType(
      final LifecycleState lifecycleState) {
    return switch (lifecycleState) {
      case CREATING -> ZeebeTaskListenerEventType.creating;
      case ASSIGNING, CLAIMING -> ZeebeTaskListenerEventType.assigning;
      case UPDATING -> ZeebeTaskListenerEventType.updating;
      case COMPLETING -> ZeebeTaskListenerEventType.completing;
      case CANCELING -> ZeebeTaskListenerEventType.canceling;
      default ->
          throw new IllegalArgumentException(
              "Unexpected user task lifecycle state: '%s'".formatted(lifecycleState));
    };
  }

  private UserTaskIntent mapDeniedIntentToResponseIntent(final UserTaskIntent intent) {
    return switch (intent) {
      case COMPLETION_DENIED -> UserTaskIntent.COMPLETE;
      case ASSIGNMENT_DENIED -> UserTaskIntent.ASSIGN;
      case UPDATE_DENIED -> UserTaskIntent.UPDATE;
      default ->
          throw new IllegalArgumentException("Unexpected user task intent: '%s'".formatted(intent));
    };
  }

  private String mapDeniedIntentToResponseRejectionReason(
      final UserTaskIntent intent, final long userTaskKey, final String deniedReason) {
    return switch (intent) {
      case COMPLETION_DENIED -> USER_TASK_COMPLETION_REJECTION.formatted(userTaskKey, deniedReason);
      case ASSIGNMENT_DENIED -> USER_TASK_ASSIGNMENT_REJECTION.formatted(userTaskKey, deniedReason);
      case UPDATE_DENIED -> USER_TASK_UPDATE_REJECTION.formatted(userTaskKey, deniedReason);
      default ->
          throw new IllegalArgumentException("Unexpected user task intent: '%s'".formatted(intent));
    };
  }

  private UserTaskCommandProcessor determineProcessorFromUserTaskLifecycleState(
      final LifecycleState lifecycleState) {

    final var userTaskIntent =
        switch (lifecycleState) {
          case CREATING -> UserTaskIntent.CREATE;
          case ASSIGNING -> UserTaskIntent.ASSIGN;
          case CLAIMING -> UserTaskIntent.CLAIM;
          case UPDATING -> UserTaskIntent.UPDATE;
          case COMPLETING -> UserTaskIntent.COMPLETE;
          case CANCELING -> UserTaskIntent.CANCEL;
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
