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
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableScriptTask;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.util.buffer.BufferUtil;

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
  public void onActivate(final ExecutableScriptTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onActivate(element, context);
  }

  @Override
  public void onComplete(final ExecutableScriptTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onComplete(element, context);
  }

  @Override
  public void onTerminate(final ExecutableScriptTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onTerminate(element, context);
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
    private final BpmnJobBehavior jobBehavior;
    private final BpmnStateBehavior stateBehavior;
    private final ExpressionProcessor expressionProcessor;
    private final VariableBehavior variableBehavior;

    public ZeebeScriptBehavior(
        final BpmnBehaviors bpmnBehaviors,
        final BpmnStateTransitionBehavior stateTransitionBehavior) {
      eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
      incidentBehavior = bpmnBehaviors.incidentBehavior();
      this.stateTransitionBehavior = stateTransitionBehavior;
      variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
      jobBehavior = bpmnBehaviors.jobBehavior();
      stateBehavior = bpmnBehaviors.stateBehavior();
      expressionProcessor = bpmnBehaviors.expressionBehavior();
      variableBehavior = bpmnBehaviors.variableBehavior();
    }

    @Override
    public void onActivate(final ExecutableScriptTask element, final BpmnElementContext context) {
      variableMappingBehavior
          .applyInputMappings(context, element)
          .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, context))
          .ifRightOrLeft(
              ok -> {
                final var activated = stateTransitionBehavior.transitionToActivated(context);
                stateTransitionBehavior.completeElement(activated);
              },
              failure -> incidentBehavior.createIncident(failure, context));
    }

    @Override
    public void onComplete(final ExecutableScriptTask element, final BpmnElementContext context) {

      final long scopeKey = variableMappingBehavior.getVariableScopeKey(context);

      expressionProcessor
          .evaluateAnyExpression(element.getExpression(), context.getElementInstanceKey())
          .map(
              result -> {
                variableBehavior.setLocalVariable(
                    scopeKey,
                    context.getProcessDefinitionKey(),
                    context.getProcessInstanceKey(),
                    context.getBpmnProcessId(),
                    BufferUtil.wrapString(element.getResultVariable()),
                    result,
                    0,
                    result.capacity());
                return null;
              });

      variableMappingBehavior
          .applyOutputMappings(context, element)
          .flatMap(
              ok -> {
                eventSubscriptionBehavior.unsubscribeFromEvents(context);
                return stateTransitionBehavior.transitionToCompleted(element, context);
              })
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, context));
    }

    @Override
    public void onTerminate(final ExecutableScriptTask element, final BpmnElementContext context) {
      final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

      eventSubscriptionBehavior.unsubscribeFromEvents(context);
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
        final ExecutableScriptTask element, final BpmnElementContext activating) {
      delegate.onActivate(element, activating);
    }

    @Override
    public void onComplete(
        final ExecutableScriptTask element, final BpmnElementContext completing) {
      delegate.onComplete(element, completing);
    }

    @Override
    public void onTerminate(
        final ExecutableScriptTask element, final BpmnElementContext terminating) {
      delegate.onTerminate(element, terminating);
    }
  }

  /** Extract different behaviors depending on the type of task. */
  private interface ScriptTaskBehavior {
    void onActivate(ExecutableScriptTask element, BpmnElementContext activating);

    void onComplete(ExecutableScriptTask element, BpmnElementContext completing);

    void onTerminate(ExecutableScriptTask element, BpmnElementContext terminating);
  }
}
