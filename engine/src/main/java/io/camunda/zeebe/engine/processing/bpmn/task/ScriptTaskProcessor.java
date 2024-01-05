/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableScriptTask;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public final class ScriptTaskProcessor
    extends JobWorkerTaskSupportingProcessor<ExecutableScriptTask> {

  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final ExpressionProcessor expressionProcessor;

  private final EventTriggerBehavior eventTriggerBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;

  public ScriptTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    super(bpmnBehaviors, stateTransitionBehavior);
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    eventTriggerBehavior = bpmnBehaviors.eventTriggerBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
  }

  @Override
  public Class<ExecutableScriptTask> getType() {
    return ExecutableScriptTask.class;
  }

  @Override
  protected boolean isJobBehavior(
      final ExecutableScriptTask element, final BpmnElementContext context) {
    if (element.getExpression() != null) {
      return false;
    }
    if (element.getJobWorkerProperties() == null) {
      throw new BpmnProcessingException(
          context, "Expected to process script task, but could not determine processing behavior");
    }
    return true;
  }

  @Override
  protected void onActivateInternal(
      final ExecutableScriptTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(ok -> evaluateScript(element, context))
        .ifRightOrLeft(
            ok -> {
              final var activated =
                  stateTransitionBehavior.transitionToActivated(context, element.getEventType());
              stateTransitionBehavior.completeElement(activated);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  protected void onCompleteInternal(
      final ExecutableScriptTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyOutputMappings(context, element)
        .flatMap(
            ok -> {
              compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
              return stateTransitionBehavior.transitionToCompleted(element, context);
            })
        .ifRightOrLeft(
            completed -> {
              compensationSubscriptionBehaviour.completeCompensationHandler(context, element);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  protected void onTerminateInternal(
      final ExecutableScriptTask element, final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

    incidentBehavior.resolveIncidents(context);

    eventSubscriptionBehavior
        .findEventTrigger(context)
        .filter(eventTrigger -> flowScopeInstance.isActive())
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

  private void triggerProcessEventWithResultVariable(
      final BpmnElementContext context,
      final String resultVariableName,
      final DirectBuffer result) {
    final DirectBuffer resultVariable = serializeToNamedVariable(resultVariableName, result);
    eventTriggerBehavior.triggeringProcessEvent(
        context.getProcessDefinitionKey(),
        context.getProcessInstanceKey(),
        context.getTenantId(),
        context.getElementInstanceKey(),
        context.getElementId(),
        resultVariable);
  }

  private static DirectBuffer serializeToNamedVariable(
      final String name, final DirectBuffer value) {
    final var resultBuffer = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter();
    writer.wrap(resultBuffer, 0);
    writer.writeMapHeader(1);
    writer.writeString(BufferUtil.wrapString(name));
    writer.writeRaw(value);
    return resultBuffer;
  }

  private Either<Failure, DirectBuffer> evaluateScript(
      final ExecutableScriptTask element, final BpmnElementContext context) {
    final var resultOrFailure =
        expressionProcessor.evaluateAnyExpression(
            element.getExpression(), context.getElementInstanceKey());

    resultOrFailure.ifRight(
        result ->
            triggerProcessEventWithResultVariable(context, element.getResultVariable(), result));

    return resultOrFailure;
  }
}
