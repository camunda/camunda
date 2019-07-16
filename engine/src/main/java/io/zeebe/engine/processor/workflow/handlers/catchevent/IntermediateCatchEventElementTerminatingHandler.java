/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.catchevent;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.element.ElementTerminatingHandler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class IntermediateCatchEventElementTerminatingHandler<T extends ExecutableCatchEventElement>
    extends ElementTerminatingHandler<T> {
  private final CatchEventSubscriber catchEventSubscriber;

  public IntermediateCatchEventElementTerminatingHandler(
      CatchEventSubscriber catchEventSubscriber) {
    super();
    this.catchEventSubscriber = catchEventSubscriber;
  }

  public IntermediateCatchEventElementTerminatingHandler(
      WorkflowInstanceIntent nextState, CatchEventSubscriber catchEventSubscriber) {
    super(nextState);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      catchEventSubscriber.unsubscribeFromEvents(context);
      return true;
    }

    return false;
  }
}
