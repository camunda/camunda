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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;

public final class UserTaskProcessor implements BpmnElementProcessor<ExecutableUserTask> {

  private final UserTaskBehavior zeebeUserTaskBehavior;
  private final UserTaskBehavior jobWorkerTaskBehavior;

  public UserTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    zeebeUserTaskBehavior = new ZeebeUserTaskBehavior(bpmnBehaviors, stateTransitionBehavior);
    jobWorkerTaskBehavior = new JobWorkerTaskBehavior(bpmnBehaviors, stateTransitionBehavior);
  }

  @Override
  public Class<ExecutableUserTask> getType() {
    return ExecutableUserTask.class;
  }

  @Override
  public void onActivate(final ExecutableUserTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onActivate(element, context);
  }

  @Override
  public void onComplete(final ExecutableUserTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onComplete(element, context);
  }

  @Override
  public void onTerminate(final ExecutableUserTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onTerminate(element, context);
  }

  private UserTaskBehavior eventBehaviorOf(
      final ExecutableUserTask element, final BpmnElementContext context) {
    if (element.getJobWorkerProperties() != null) {
      return jobWorkerTaskBehavior;
    } else if (element.getUserTaskProperties() != null) {
      return zeebeUserTaskBehavior;
    } else {
      throw new BpmnProcessingException(
          context, "Expected to process user task, but could not determine processing behavior");
    }
  }

  private static final class ZeebeUserTaskBehavior implements UserTaskBehavior {

    private final BpmnIncidentBehavior incidentBehavior;
    private final BpmnStateTransitionBehavior stateTransitionBehavior;
    private final BpmnVariableMappingBehavior variableMappingBehavior;
    private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
    private final BpmnStateBehavior stateBehavior;
    private final ExpressionProcessor expressionProcessor;

    private final EventTriggerBehavior eventTriggerBehavior;

    public ZeebeUserTaskBehavior(
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
    public void onActivate(final ExecutableUserTask element, final BpmnElementContext context) {}

    @Override
    public void onComplete(final ExecutableUserTask element, final BpmnElementContext context) {}

    @Override
    public void onTerminate(final ExecutableUserTask element, final BpmnElementContext context) {}
  }

  private static final class JobWorkerTaskBehavior implements UserTaskBehavior {

    private final JobWorkerTaskProcessor delegate;

    public JobWorkerTaskBehavior(
        final BpmnBehaviors bpmnBehaviors,
        final BpmnStateTransitionBehavior stateTransitionBehavior) {
      delegate = new JobWorkerTaskProcessor(bpmnBehaviors, stateTransitionBehavior);
    }

    @Override
    public void onActivate(final ExecutableUserTask element, final BpmnElementContext activating) {
      delegate.onActivate(element, activating);
    }

    @Override
    public void onComplete(final ExecutableUserTask element, final BpmnElementContext completing) {
      delegate.onComplete(element, completing);
    }

    @Override
    public void onTerminate(
        final ExecutableUserTask element, final BpmnElementContext terminating) {
      delegate.onTerminate(element, terminating);
    }
  }

  /** Extract different behaviors depending on the type of task. */
  private interface UserTaskBehavior {
    void onActivate(ExecutableUserTask element, BpmnElementContext activating);

    void onComplete(ExecutableUserTask element, BpmnElementContext completing);

    void onTerminate(ExecutableUserTask element, BpmnElementContext terminating);
  }
}
