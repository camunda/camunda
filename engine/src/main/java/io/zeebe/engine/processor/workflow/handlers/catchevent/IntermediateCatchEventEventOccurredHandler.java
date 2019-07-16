/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.catchevent;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class IntermediateCatchEventEventOccurredHandler<T extends ExecutableCatchEventElement>
    extends EventOccurredHandler<T> {
  public IntermediateCatchEventEventOccurredHandler() {
    this(null);
  }

  public IntermediateCatchEventEventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      final EventTrigger event = getTriggeredEvent(context, context.getKey());
      if (event == null) {
        Loggers.WORKFLOW_PROCESSOR_LOGGER.debug(
            "Processing EVENT_OCCURRED but no event trigger found for element {}",
            context.getElementInstance());
        return false;
      }

      processEventTrigger(context, context.getKey(), context.getKey(), event);
      transitionTo(context, WorkflowInstanceIntent.ELEMENT_COMPLETING);
      return true;
    }

    return false;
  }
}
