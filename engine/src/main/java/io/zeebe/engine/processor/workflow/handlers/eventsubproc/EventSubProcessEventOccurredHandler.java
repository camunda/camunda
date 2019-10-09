/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.eventsubproc;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public class EventSubProcessEventOccurredHandler<T extends ExecutableStartEvent>
    extends EventOccurredHandler<T> {
  private final WorkflowInstanceRecord containerRecord = new WorkflowInstanceRecord();

  public EventSubProcessEventOccurredHandler() {
    super(null);
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    final WorkflowInstanceRecord event = context.getValue();

    final long scopeKey = context.getValue().getFlowScopeKey();
    final EventTrigger triggeredEvent = getTriggeredEvent(context, scopeKey);
    if (triggeredEvent == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error("No triggered event for key {}", context.getKey());
      return false;
    }

    activateContainer(context, event);
    final WorkflowInstanceRecord startRecord =
        getEventRecord(context, triggeredEvent, BpmnElementType.START_EVENT)
            .setWorkflowInstanceKey(event.getWorkflowInstanceKey())
            .setVersion(event.getVersion())
            .setBpmnProcessId(event.getBpmnProcessId())
            .setFlowScopeKey(context.getKey());

    deferEvent(context, event.getWorkflowKey(), context.getKey(), startRecord, triggeredEvent);

    return true;
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<T> context) {
    return isElementActive(context.getFlowScopeInstance());
  }

  private void activateContainer(final BpmnStepContext<T> context, WorkflowInstanceRecord event) {
    final DirectBuffer subprocessId = context.getElement().getEventSubProcess();

    containerRecord.reset();
    containerRecord
        .setElementId(subprocessId)
        .setBpmnElementType(BpmnElementType.SUB_PROCESS)
        .setBpmnProcessId(event.getBpmnProcessId())
        .setWorkflowKey(event.getWorkflowKey())
        .setVersion(event.getVersion())
        .setWorkflowInstanceKey(event.getWorkflowInstanceKey())
        .setFlowScopeKey(event.getFlowScopeKey());

    context
        .getOutput()
        .appendFollowUpEvent(
            context.getKey(), WorkflowInstanceIntent.ELEMENT_ACTIVATING, containerRecord);
  }
}
