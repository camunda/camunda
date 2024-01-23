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
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.util.Either;

public abstract class JobWorkerTaskSupportingProcessor<T extends ExecutableJobWorkerTask>
    implements BpmnElementProcessor<T> {

  private final JobWorkerTaskProcessor delegate;

  public JobWorkerTaskSupportingProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    delegate = new JobWorkerTaskProcessor(bpmnBehaviors, stateTransitionBehavior);
  }

  @Override
  public Either<Failure, Void> onActivate(final T element, final BpmnElementContext context) {
    if (isJobBehavior(element, context)) {
      return delegate.onActivate(element, context);
    } else {
      return onActivateInternal(element, context);
    }
  }

  @Override
  public void finalizeActivation(final T element, final BpmnElementContext context) {
    if (isJobBehavior(element, context)) {
      delegate.finalizeActivation(element, context);
    } else {
      System.out.println("TODO implement `finalizeActivationInternal` method if needed");
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
  public void finalizeCompletion(final T element, final BpmnElementContext context) {
    if (isJobBehavior(element, context)) {
      delegate.finalizeCompletion(element, context);
    } else {
      System.out.println("TODO implement `finalizeCompletionInternal` method if needed");
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

  protected abstract Either<Failure, Void> onActivateInternal(
      final T element, final BpmnElementContext context);

  protected abstract void onCompleteInternal(final T element, final BpmnElementContext context);

  protected abstract void onTerminateInternal(final T element, final BpmnElementContext context);
}
