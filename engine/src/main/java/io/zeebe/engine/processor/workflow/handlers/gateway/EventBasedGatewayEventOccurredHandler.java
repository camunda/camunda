/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.gateway;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableEventBasedGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;

// todo: this skips the sequence flow taken and just starts the next element
// https://github.com/zeebe-io/zeebe/issues/1979
public class EventBasedGatewayEventOccurredHandler<T extends ExecutableEventBasedGateway>
    extends EventOccurredHandler<T> {
  public EventBasedGatewayEventOccurredHandler() {
    super();
  }

  public EventBasedGatewayEventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      final EventTrigger event = getTriggeredEvent(context, context.getKey());
      final ExecutableSequenceFlow flow = getSequenceFlow(context, event);

      if (flow == null) {
        Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
            "No outgoing flow has a target with ID {} for process {}",
            BufferUtil.bufferAsString(event.getElementId()),
            BufferUtil.bufferAsString(context.getValue().getBpmnProcessIdBuffer()));
        return false;
      }

      final WorkflowInstanceRecord eventRecord =
          getEventRecord(context, event, flow.getTarget().getElementType());
      deferEvent(context, context.getKey(), context.getKey(), eventRecord, event);
      return true;
    }

    return false;
  }

  private ExecutableSequenceFlow getSequenceFlow(BpmnStepContext<T> context, EventTrigger event) {
    final List<ExecutableSequenceFlow> outgoing = context.getElement().getOutgoing();

    for (final ExecutableSequenceFlow flow : outgoing) {
      if (flow.getTarget().getId().equals(event.getElementId())) {
        return flow;
      }
    }

    return null;
  }
}
