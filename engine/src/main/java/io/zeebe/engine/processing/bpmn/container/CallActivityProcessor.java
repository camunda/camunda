/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.container;

import io.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.state.deployment.DeployedProcess;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.Either;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class CallActivityProcessor
    implements BpmnElementContainerProcessor<ExecutableCallActivity> {

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

  public CallActivityProcessor(final BpmnBehaviors bpmnBehaviors) {
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
  }

  @Override
  public Class<ExecutableCallActivity> getType() {
    return ExecutableCallActivity.class;
  }

  @Override
  public void onActivating(final ExecutableCallActivity element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, context))
        .flatMap(ok -> evaluateProcessId(context, element))
        .flatMap(this::getProcessForProcessId)
        .flatMap(this::checkProcessHasNoneStartEvent)
        .ifRightOrLeft(
            process -> {
              stateTransitionBehavior.transitionToActivated(context);

              final var childProcessInstanceKey =
                  stateTransitionBehavior.createChildProcessInstance(process, context);

              final var callActivityInstance = stateBehavior.getElementInstance(context);
              callActivityInstance.setCalledChildInstanceKey(childProcessInstanceKey);
              stateBehavior.updateElementInstance(callActivityInstance);

              final var callActivityInstanceKey = context.getElementInstanceKey();
              stateBehavior.copyVariablesToProcessInstance(
                  callActivityInstanceKey, childProcessInstanceKey, process);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onActivated(final ExecutableCallActivity element, final BpmnElementContext context) {
    // Nothing to do but wait until the child process has completed
  }

  @Override
  public void onCompleting(final ExecutableCallActivity element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyOutputMappings(context, element)
        .ifRightOrLeft(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(context);
              stateTransitionBehavior.transitionToCompleted(context);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onCompleted(final ExecutableCallActivity element, final BpmnElementContext context) {
    stateTransitionBehavior.takeOutgoingSequenceFlows(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    stateTransitionBehavior.terminateChildProcessInstance(context);
    eventSubscriptionBehavior.unsubscribeFromEvents(context);
  }

  @Override
  public void onTerminated(final ExecutableCallActivity element, final BpmnElementContext context) {
    eventSubscriptionBehavior.publishTriggeredBoundaryEvent(context);
    incidentBehavior.resolveIncidents(context);
    stateTransitionBehavior.onElementTerminated(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(
      final ExecutableCallActivity element, final BpmnElementContext context) {
    eventSubscriptionBehavior.triggerBoundaryEvent(element, context);
  }

  @Override
  public void onChildActivating(
      final ExecutableCallActivity element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {}

  @Override
  public void onChildCompleted(
      final ExecutableCallActivity element,
      final BpmnElementContext callActivityContext,
      final BpmnElementContext childContext) {
    final var currentState = callActivityContext.getIntent();
    switch (currentState) {
      case ELEMENT_ACTIVATED:
        stateTransitionBehavior.transitionToCompleting(callActivityContext);

        if (element.getOutputMappings().isPresent()
            || element.isPropagateAllChildVariablesEnabled()) {
          stateBehavior.propagateTemporaryVariables(childContext, callActivityContext);
        }

        break;
      case ELEMENT_TERMINATING:
        // the call activity is already terminating, it doesn't matter that the child completed
        // do nothing
        break;
      default:
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
    if (currentState == ProcessInstanceIntent.ELEMENT_TERMINATING) {
      stateTransitionBehavior.transitionToTerminated(callActivityContext);
    } else {
      final var message = String.format(UNABLE_TO_TERMINATE_FROM_STATE_MESSAGE, currentState);
      throw new BpmnProcessingException(callActivityContext, message);
    }
  }

  private Either<Failure, DirectBuffer> evaluateProcessId(
      final BpmnElementContext context, final ExecutableCallActivity element) {
    final var processIdExpression = element.getCalledElementProcessId();
    final var scopeKey = context.getElementInstanceKey();
    return expressionProcessor.evaluateStringExpressionAsDirectBuffer(
        processIdExpression, scopeKey);
  }

  private Either<Failure, DeployedProcess> getProcessForProcessId(final DirectBuffer processId) {
    final var process = stateBehavior.getLatestProcessVersion(processId);
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
