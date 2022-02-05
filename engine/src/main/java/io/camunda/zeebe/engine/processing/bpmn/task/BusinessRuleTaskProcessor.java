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
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBusinessRuleTask;

public final class BusinessRuleTaskProcessor
    implements BpmnElementProcessor<ExecutableBusinessRuleTask> {

  private final BusinessRuleTaskBehavior calledDecisionBehavior;
  private final BusinessRuleTaskBehavior jobWorkerTaskBehavior;

  public BusinessRuleTaskProcessor(final BpmnBehaviors bpmnBehaviors) {
    calledDecisionBehavior = new CalledDecisionBehavior();
    jobWorkerTaskBehavior = new JobWorkerTaskBehavior(bpmnBehaviors);
  }

  @Override
  public Class<ExecutableBusinessRuleTask> getType() {
    return ExecutableBusinessRuleTask.class;
  }

  @Override
  public void onActivate(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    eventBehaviorOf(element).onActivate(element, context);
  }

  @Override
  public void onComplete(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    eventBehaviorOf(element).onComplete(element, context);
  }

  @Override
  public void onTerminate(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    eventBehaviorOf(element).onTerminate(element, context);
  }

  private BusinessRuleTaskBehavior eventBehaviorOf(final ExecutableBusinessRuleTask element) {
    if (element.getDecisionId() != null) {
      return calledDecisionBehavior;
    } else if (element.getJobWorkerProperties() != null) {
      return jobWorkerTaskBehavior;
    } else {
      throw new IllegalArgumentException(
          "Expected to process business rule task, but could not determine processing behavior");
    }
  }

  private static final class CalledDecisionBehavior implements BusinessRuleTaskBehavior {

    @Override
    public void onActivate(
        final ExecutableBusinessRuleTask element, final BpmnElementContext activating) {
      // todo: implement
    }

    @Override
    public void onComplete(
        final ExecutableBusinessRuleTask element, final BpmnElementContext completing) {
      // todo: implement
    }

    @Override
    public void onTerminate(
        final ExecutableBusinessRuleTask element, final BpmnElementContext terminating) {
      // todo: implement
    }
  }

  private static final class JobWorkerTaskBehavior implements BusinessRuleTaskBehavior {

    private final JobWorkerTaskProcessor delegate;

    public JobWorkerTaskBehavior(final BpmnBehaviors bpmnBehaviors) {
      delegate = new JobWorkerTaskProcessor(bpmnBehaviors);
    }

    @Override
    public void onActivate(
        final ExecutableBusinessRuleTask element, final BpmnElementContext activating) {
      delegate.onActivate(element, activating);
    }

    @Override
    public void onComplete(
        final ExecutableBusinessRuleTask element, final BpmnElementContext completing) {
      delegate.onComplete(element, completing);
    }

    @Override
    public void onTerminate(
        final ExecutableBusinessRuleTask element, final BpmnElementContext terminating) {
      delegate.onTerminate(element, terminating);
    }
  }

  /** Extract different behaviors depending on the type of event. */
  private interface BusinessRuleTaskBehavior {
    void onActivate(ExecutableBusinessRuleTask element, BpmnElementContext activating);

    void onComplete(ExecutableBusinessRuleTask element, BpmnElementContext completing);

    void onTerminate(ExecutableBusinessRuleTask element, BpmnElementContext terminating);
  }
}
