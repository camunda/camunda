/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior.UserTaskProperties;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public final class UserTaskProcessor extends JobWorkerTaskSupportingProcessor<ExecutableUserTask> {

  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnUserTaskBehavior userTaskBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;
  private final BpmnJobBehavior jobBehavior;
  private final StateWriter stateWriter;
  private final UserTaskState userTaskState;

  public UserTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior,
      final Writers writers,
      final UserTaskState userTaskState) {
    super(bpmnBehaviors, stateTransitionBehavior);
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    userTaskBehavior = bpmnBehaviors.userTaskBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
    jobBehavior = bpmnBehaviors.jobBehavior();
    stateWriter = writers.state();
    this.userTaskState = userTaskState;
  }

  @Override
  public Class<ExecutableUserTask> getType() {
    return ExecutableUserTask.class;
  }

  @Override
  protected boolean isJobBehavior(
      final ExecutableUserTask element, final BpmnElementContext context) {
    if (element.getUserTaskProperties() != null) {
      return false;
    }
    if (element.getJobWorkerProperties() == null) {
      throw new BpmnProcessingException(
          context, "Expected to process user task, but could not determine processing behavior");
    }
    return true;
  }

  @Override
  protected Either<Failure, ?> onActivateInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    return variableMappingBehavior.applyInputMappings(context, element);
  }

  @Override
  protected Either<Failure, ?> onFinalizeActivationInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    return userTaskBehavior
        .evaluateUserTaskExpressions(element, context)
        .flatMap(j -> eventSubscriptionBehavior.subscribeToEvents(element, context).map(ok -> j))
        .map(userTaskProperties -> createUserTask(element, context, userTaskProperties))
        .thenDo(
            ok -> stateTransitionBehavior.transitionToActivated(context, element.getEventType()))
        .thenDo(
            result -> {
              if (result.hasAssigneeProp()) {
                assignUserTask(element, context, result.task(), result.getAssigneeProp());
              }
            });
  }

  @Override
  protected Either<Failure, ?> onCompleteInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    return variableMappingBehavior
        .applyOutputMappings(context, element)
        .thenDo(ok -> eventSubscriptionBehavior.unsubscribeFromEvents(context));
  }

  @Override
  protected Either<Failure, ?> onFinalizeCompletionInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
    return stateTransitionBehavior
        .transitionToCompleted(element, context)
        .thenDo(
            completed -> {
              compensationSubscriptionBehaviour.completeCompensationHandler(completed);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            });
  }

  @Override
  protected TransitionState onTerminateInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {

    if (element.hasExecutionListeners() || element.hasTaskListeners()) {
      jobBehavior.cancelJob(context);
    }

    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    incidentBehavior.resolveIncidents(context);

    final var elementInstance = stateBehavior.getElementInstance(context);

    final Optional<UserTaskRecord> userTaskRecord =
        userTaskBehavior.userTaskCanceling(elementInstance);
    if (userTaskRecord.isPresent()) {
      if (element.hasTaskListeners(ZeebeTaskListenerEventType.canceling)) {
        final var firstListener =
            element.getTaskListeners(ZeebeTaskListenerEventType.canceling).getFirst();
        jobBehavior.createNewTaskListenerJob(context, userTaskRecord.get(), firstListener);
        return TransitionState.AWAIT;
      } else {
        userTaskBehavior.userTaskCanceled(elementInstance);
        return TransitionState.CONTINUE;
      }
    } else {
      return TransitionState.CONTINUE;
    }
  }

  @Override
  protected void onFinalizeTerminateInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);
    eventSubscriptionBehavior
        .findEventTrigger(context)
        .filter(eventTrigger -> flowScopeInstance.isActive())
        .filter(eventTrigger -> !flowScopeInstance.isInterrupted())
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
              eventSubscriptionBehavior.activateTriggeredEvent(
                  context.getElementInstanceKey(),
                  terminated.getFlowScopeKey(),
                  eventTrigger,
                  terminated);
            },
            () -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
              stateTransitionBehavior.onElementTerminated(element, terminated);
            });
  }

  private UserTaskCreationResult createUserTask(
      final ExecutableUserTask element,
      final BpmnElementContext context,
      final UserTaskProperties userTaskProperties) {
    final var userTaskRecord =
        userTaskBehavior.createNewUserTask(context, element, userTaskProperties);
    userTaskBehavior.userTaskCreated(userTaskRecord);
    return new UserTaskCreationResult(userTaskProperties, userTaskRecord);
  }

  private void assignUserTask(
      final ExecutableUserTask element,
      final BpmnElementContext context,
      final UserTaskRecord userTaskRecord,
      final String assignee) {
    userTaskBehavior.userTaskAssigning(userTaskRecord, assignee);
    element.getTaskListeners(ZeebeTaskListenerEventType.assigning).stream()
        .findFirst()
        .ifPresentOrElse(
            listener -> jobBehavior.createNewTaskListenerJob(context, userTaskRecord, listener),
            () -> userTaskBehavior.userTaskAssigned(userTaskRecord, assignee));
  }

  private record UserTaskCreationResult(UserTaskProperties props, UserTaskRecord task) {

    public String getAssigneeProp() {
      return props.getAssignee();
    }

    public boolean hasAssigneeProp() {
      return StringUtils.isNotEmpty(getAssigneeProp());
    }
  }
}
