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
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;

public abstract class JobWorkerTaskSupportingProcessor<T extends ExecutableJobWorkerTask>
    implements BpmnElementProcessor<T> {

  private final JobWorkerTaskProcessor delegate;

  public JobWorkerTaskSupportingProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    delegate = new JobWorkerTaskProcessor(bpmnBehaviors, stateTransitionBehavior);
  }

  @Override
  public void onActivate(final T element, final BpmnElementContext context) {
    if (isJobBehavior(element, context)) {
      delegate.onActivate(element, context);
    } else {
      onActivateInternal(element, context);
    }
  }

  @Override
  public void onComplete(final T element, final BpmnElementContext context) {
    if (isJobBehavior(element, context)) {
      delegate.onComplete(element, context);
    } else {
      onCompleteInternal(element, context);
    }
  }

  @Override
  public void onTerminate(final T element, final BpmnElementContext context) {
    if (isJobBehavior(element, context)) {
      delegate.onTerminate(element, context);
    } else {
      onTerminateInternal(element, context);
    }
  }

  protected abstract boolean isJobBehavior(final T element, final BpmnElementContext context);

  protected abstract void onActivateInternal(final T element, final BpmnElementContext context);

  protected abstract void onCompleteInternal(final T element, final BpmnElementContext context);

  protected abstract void onTerminateInternal(final T element, final BpmnElementContext context);
}
