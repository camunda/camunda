/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class CallActivityProcessor
    implements BpmnElementContainerProcessor<ExecutableCallActivity> {

  public static final String MAX_DEPTH_EXCEEDED_MESSAGE =
      """
      The call activity has reached the maximum depth of %d. \
      This is likely due to a recursive call. \
      Cancel the root process instance if this was unintentional. \
      Otherwise, consider increasing the maximum depth, \
      or use process instance modification to adjust the process instance.""";
  private static final String UNABLE_TO_COMPLETE_FROM_STATE_MESSAGE =
      "Expected to complete call activity after child completed, but call activity cannot be completed from state '%s'";
  private static final String UNABLE_TO_TERMINATE_FROM_STATE_MESSAGE =
      "Expected to terminate call activity after child terminated, but call activity cannot be terminated from state '%s'";

  private final ExpressionProcessor expressionProcessor;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;
  private final int maxProcessDepth;

  public CallActivityProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior,
      final int maxProcessDepth) {
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    stateBehavior = bpmnBehaviors.stateBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
    this.maxProcessDepth = maxProcessDepth;
  }

  @Override
  public Class<ExecutableCallActivity> getType() {
    return ExecutableCallActivity.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    return variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(ok -> validateProcessDepth(context))
        .flatMap(ok -> evaluateProcessId(context, element))
        .flatMap(processId -> getProcessForProcessId(processId, context.getTenantId()))
        .flatMap(this::checkProcessHasNoneStartEvent)
        .flatMap(p -> eventSubscriptionBehavior.subscribeToEvents(element, context).map(ok -> p))
        .thenDo(
            process -> {
              final var activated =
                  stateTransitionBehavior.transitionToActivated(context, element.getEventType());

              final var childProcessInstanceKey =
                  stateTransitionBehavior.createChildProcessInstance(process, context);

              final var propagateAllParentVariablesEnabled =
                  element.isPropagateAllParentVariablesEnabled();
              final var inputMappings = element.getInputMappings();
              final var callActivityInstanceKey = activated.getElementInstanceKey();

              if (propagateAllParentVariablesEnabled) {
                stateBehavior.copyAllVariablesToProcessInstance(
                    callActivityInstanceKey, childProcessInstanceKey, process);
              } else if (inputMappings.isPresent()) {
                // when activating the call activity, the input mappings will be applied.
                // Resulting in local variables in the (local) call activity scope.
                // These local variables can simply be propagated to the called child
                // process instance.
                stateBehavior.copyLocalVariablesToProcessInstance(
                    callActivityInstanceKey, childProcessInstanceKey, process);
              }
            });
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    return variableMappingBehavior
        .applyOutputMappings(context, element)
        .flatMap(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(context);
              compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
              return stateTransitionBehavior.transitionToCompleted(element, context);
            })
        .thenDo(
            completed -> {
              compensationSubscriptionBehaviour.completeCompensationHandler(completed);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            });
  }

  @Override
  public void onTerminate(final ExecutableCallActivity element, final BpmnElementContext context) {
    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    incidentBehavior.resolveIncidents(context);
    stateTransitionBehavior.terminateChildProcessInstance(this, element, context);
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableCallActivity element,
      final BpmnElementContext callActivityContext,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {
    final var currentState = callActivityContext.getIntent();

    if (currentState == ProcessInstanceIntent.ELEMENT_ACTIVATED) {
      stateTransitionBehavior.completeElement(callActivityContext);

    } else if (currentState == ProcessInstanceIntent.ELEMENT_TERMINATING) {
      // the call activity is interrupted concurrently (e.g. by a boundary event)
      transitionToTerminated(element, callActivityContext);

    } else {
      final var message = String.format(UNABLE_TO_COMPLETE_FROM_STATE_MESSAGE, currentState);
      throw new BpmnProcessingException(callActivityContext, message);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableCallActivity element,
      final BpmnElementContext callActivityContext,
      final BpmnElementContext childContext) {
    final var currentState = callActivityContext.getIntent();
    if (currentState != ProcessInstanceIntent.ELEMENT_TERMINATING) {
      final var message = String.format(UNABLE_TO_TERMINATE_FROM_STATE_MESSAGE, currentState);
      throw new BpmnProcessingException(callActivityContext, message);
    }

    transitionToTerminated(element, callActivityContext);
  }

  /**
   * Returns a failure if the process depth of the called instance is about to exceed the maximum
   * allowed depth. Otherwise, returns a right.
   */
  private Either<Failure, Void> validateProcessDepth(final BpmnElementContext context) {
    final var processInstance = stateBehavior.getElementInstance(context.getProcessInstanceKey());
    final int processDepth = processInstance.getProcessDepth();
    final var isExceedingMaxDepth = (processDepth + 1) > maxProcessDepth;
    if (isExceedingMaxDepth) {
      final var message = MAX_DEPTH_EXCEEDED_MESSAGE.formatted(maxProcessDepth);
      return Either.left(new Failure(message, ErrorType.CALLED_ELEMENT_ERROR));
    }
    return Either.right(null);
  }

  private void transitionToTerminated(
      final ExecutableCallActivity element, final BpmnElementContext context) {
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

  private Either<Failure, DirectBuffer> evaluateProcessId(
      final BpmnElementContext context, final ExecutableCallActivity element) {
    final var processIdExpression = element.getCalledElementProcessId();
    final var scopeKey = context.getElementInstanceKey();
    return expressionProcessor.evaluateStringExpressionAsDirectBuffer(
        processIdExpression, scopeKey);
  }

  private Either<Failure, DeployedProcess> getProcessForProcessId(
      final DirectBuffer processId, final String tenantId) {
    final var process = stateBehavior.getLatestProcessVersion(processId, tenantId);
    if (process.isPresent()) {
      return Either.right(process.get());
    }
    return Either.left(
        new Failure(
            String.format(
                "Expected process with BPMN process id '%s' to be deployed, but not found.",
                BufferUtil.bufferAsString(processId)),
            ErrorType.CALLED_ELEMENT_ERROR));
  }

  private Either<Failure, DeployedProcess> checkProcessHasNoneStartEvent(
      final DeployedProcess process) {
    if (process.getProcess().getNoneStartEvent() == null) {
      return Either.left(
          new Failure(
              String.format(
                  "Expected process with BPMN process id '%s' to have a none start event, but not found.",
                  BufferUtil.bufferAsString(process.getBpmnProcessId())),
              ErrorType.CALLED_ELEMENT_ERROR));
    }
    return Either.right(process);
  }
}
