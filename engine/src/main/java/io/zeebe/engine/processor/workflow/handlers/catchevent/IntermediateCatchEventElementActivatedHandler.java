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
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatedHandler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class IntermediateCatchEventElementActivatedHandler<T extends ExecutableCatchEventElement>
    extends ElementActivatedHandler<T> {
  public IntermediateCatchEventElementActivatedHandler() {
    this(null);
  }

  public IntermediateCatchEventElementActivatedHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      if (context.getElement().isNone()) {
        transitionTo(context, WorkflowInstanceIntent.ELEMENT_COMPLETING);
      }

      return true;
    }

    return false;
  }
}
