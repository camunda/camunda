/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.activity;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableBoundaryEvent;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;

public class ActivityEventOccurredHandler<T extends ExecutableActivity>
    extends EventOccurredHandler<T> {
  public ActivityEventOccurredHandler() {
    this(null);
  }

  public ActivityEventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final EventTrigger event = getTriggeredEvent(context, context.getKey());
    final ExecutableBoundaryEvent boundaryEvent = getBoundaryEvent(context, event);
    if (boundaryEvent == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
          "No boundary event found with ID {} for process {}",
          BufferUtil.bufferAsString(event.getElementId()),
          BufferUtil.bufferAsString(context.getValue().getBpmnProcessIdBuffer()));
      return false;
    }

    final WorkflowInstanceRecord eventRecord =
        getEventRecord(context, event, boundaryEvent.getElementType());
    if (boundaryEvent.cancelActivity()) {
      transitionTo(context, WorkflowInstanceIntent.ELEMENT_TERMINATING);
      deferEvent(context, context.getKey(), context.getKey(), eventRecord, event);
    } else {
      publishEvent(context, context.getKey(), eventRecord, event);
    }

    return true;
  }

  private ExecutableBoundaryEvent getBoundaryEvent(BpmnStepContext<T> context, EventTrigger event) {
    final List<ExecutableBoundaryEvent> boundaryEvents = context.getElement().getBoundaryEvents();
    for (final ExecutableBoundaryEvent boundaryEvent : boundaryEvents) {
      if (event.getElementId().equals(boundaryEvent.getId())) {
        return boundaryEvent;
      }
    }

    return null;
  }
}
