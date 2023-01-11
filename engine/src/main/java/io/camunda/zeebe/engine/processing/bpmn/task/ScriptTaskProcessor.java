/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableScriptTask;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffects;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public final class ScriptTaskProcessor implements BpmnElementProcessor<ExecutableScriptTask> {

  private final ScriptTaskBehavior zeebeScriptBehavior;
  private final ScriptTaskBehavior jobWorkerTaskBehavior;

  public ScriptTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    zeebeScriptBehavior = new ZeebeScriptBehavior(bpmnBehaviors, stateTransitionBehavior);
    jobWorkerTaskBehavior = new JobWorkerTaskBehavior(bpmnBehaviors, stateTransitionBehavior);
  }

  @Override
  public Class<ExecutableScriptTask> getType() {
    return ExecutableScriptTask.class;
  }

  @Override
  public void onActivate(final ExecutableScriptTask element, final BpmnElementContext context,
      final SideEffects sideEffects, final SideEffects sideEffectQueue) {
    eventBehaviorOf(element, context).onActivate(element, context, sideEffects, sideEffectQueue);
  }

  @Override
  public void onComplete(final ExecutableScriptTask element, final BpmnElementContext context,
      final SideEffects sideEffects) {
    eventBehaviorOf(element, context).onComplete(element, context, sideEffects);
  }

  @Override
  public void onTerminate(final ExecutableScriptTask element, final BpmnElementContext context,
      final SideEffects sideEffects) {
    eventBehaviorOf(element, context).onTerminate(element, context, sideEffects);
  }

  private ScriptTaskBehavior eventBehaviorOf(
      final ExecutableScriptTask element, final BpmnElementContext context) {
    if (element.getExpression() != null) {
      return zeebeScriptBehavior;
    } else if (element.getJobWorkerProperties() != null) {
      return jobWorkerTaskBehavior;
    } else {
      throw new BpmnProcessingException(
          context, "Expected to process script task, but could not determine processing behavior");
    }
  }

  private static final class ZeebeScriptBehavior implements ScriptTaskBehavior {

    private final BpmnIncidentBehavior incidentBehavior;
    private final BpmnStateTransitionBehavior stateTransitionBehavior;
    private final BpmnVariableMappingBehavior variableMappingBehavior;
    private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
    private final BpmnStateBehavior stateBehavior;
    private final ExpressionProcessor expressionProcessor;

    private final EventTriggerBehavior eventTriggerBehavior;

    public ZeebeScriptBehavior(
        final BpmnBehaviors bpmnBehaviors,
        final BpmnStateTransitionBehavior stateTransitionBehavior) {
      eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
      incidentBehavior = bpmnBehaviors.incidentBehavior();
      this.stateTransitionBehavior = stateTransitionBehavior;
      variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
      stateBehavior = bpmnBehaviors.stateBehavior();
      expressionProcessor = bpmnBehaviors.expressionBehavior();
      eventTriggerBehavior = bpmnBehaviors.eventTriggerBehavior();
    }

    @Override
    public void onActivate(final ExecutableScriptTask element, final BpmnElementContext context,
        final SideEffects sideEffects, final SideEffects sideEffectQueue) {
      variableMappingBehavior
          .applyInputMappings(context, element)
          .flatMap(ok -> evaluateScript(element, context))
          .ifRightOrLeft(
              ok -> {
                final var activated = stateTransitionBehavior.transitionToActivated(context);
                stateTransitionBehavior.completeElement(activated);
              },
              failure -> incidentBehavior.createIncident(failure, context));
    }

    @Override
    public void onComplete(final ExecutableScriptTask element, final BpmnElementContext context,
        final SideEffects sideEffects) {
      variableMappingBehavior
          .applyOutputMappings(context, element)
          .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, context))
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, context));
    }

    @Override
    public void onTerminate(final ExecutableScriptTask element, final BpmnElementContext context,
        final SideEffects sideEffects) {
      final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

      incidentBehavior.resolveIncidents(context);

      eventSubscriptionBehavior
          .findEventTrigger(context)
          .filter(eventTrigger -> flowScopeInstance.isActive())
          .ifPresentOrElse(
              eventTrigger -> {
                final var terminated = stateTransitionBehavior.transitionToTerminated(context);
                eventSubscriptionBehavior.activateTriggeredEvent(
                    context.getElementInstanceKey(),
                    terminated.getFlowScopeKey(),
                    eventTrigger,
                    terminated);
              },
              () -> {
                final var terminated = stateTransitionBehavior.transitionToTerminated(context);
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

  private static final class JobWorkerTaskBehavior implements ScriptTaskBehavior {

    private final JobWorkerTaskProcessor delegate;

    public JobWorkerTaskBehavior(
        final BpmnBehaviors bpmnBehaviors,
        final BpmnStateTransitionBehavior stateTransitionBehavior) {
      delegate = new JobWorkerTaskProcessor(bpmnBehaviors, stateTransitionBehavior);
    }

    @Override
    public void onActivate(
        final ExecutableScriptTask element, final BpmnElementContext activating,
        final SideEffects sideEffects, final SideEffects sideEffectQueue) {
      delegate.onActivate(element, activating, sideEffects, sideEffectQueue);
    }

    @Override
    public void onComplete(
        final ExecutableScriptTask element, final BpmnElementContext completing,
        final SideEffects sideEffects) {
      delegate.onComplete(element, completing, sideEffects);
    }

    @Override
    public void onTerminate(
        final ExecutableScriptTask element, final BpmnElementContext terminating,
        final SideEffects sideEffects) {
      delegate.onTerminate(element, terminating, sideEffects);
    }
  }

  /** Extract different behaviors depending on the type of task. */
  private interface ScriptTaskBehavior {
    void onActivate(ExecutableScriptTask element, BpmnElementContext activating,
        final SideEffects sideEffects, final SideEffects sideEffectQueue);

    void onComplete(ExecutableScriptTask element, BpmnElementContext completing,
        final SideEffects sideEffects);

    void onTerminate(ExecutableScriptTask element, BpmnElementContext terminating,
        final SideEffects sideEffects);
  }
}
