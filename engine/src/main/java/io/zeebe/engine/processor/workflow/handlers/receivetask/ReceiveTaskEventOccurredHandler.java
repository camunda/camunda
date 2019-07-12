/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.receivetask;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableReceiveTask;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityEventOccurredHandler;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class ReceiveTaskEventOccurredHandler<T extends ExecutableReceiveTask>
    extends ActivityEventOccurredHandler<T> {

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final EventTrigger event = getTriggeredEvent(context, context.getKey());
    if (isActivityEventHandler(context, event)) {
      processEventTrigger(context, context.getKey(), context.getKey(), event);
      transitionTo(context, WorkflowInstanceIntent.ELEMENT_COMPLETING);
      return true;
    }

    return super.handleState(context);
  }

  private boolean isActivityEventHandler(BpmnStepContext<T> context, EventTrigger event) {
    return event.getElementId().equals(context.getElement().getId());
  }
}
