/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.workflow.model.element.ExecutableFlowElement;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.broker.workflow.state.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Checks the event trigger, and if it is related to the element itself, will transition to
 * completing.
 *
 * @param <T>
 */
public class EventOccurredHandler<T extends ExecutableFlowElement> extends AbstractHandler<T> {
  private final WorkflowInstanceRecord eventRecord = new WorkflowInstanceRecord();

  public EventOccurredHandler() {
    this(WorkflowInstanceIntent.ELEMENT_COMPLETING);
  }

  public EventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context)
        && (!hasWorkflowInstance(context) || isElementActive(context.getElementInstance()));
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    return true;
  }

  /**
   * Returns the latest event trigger but does not consume it from the state. It will be consumed
   * once the caller calls {@link #handleEvent(BpmnStepContext, long, EventTrigger)}.
   */
  protected EventTrigger getTriggeredEvent(BpmnStepContext<T> context) {
    return context
        .getStateDb()
        .getEventScopeInstanceState()
        .peekEventTrigger(context.getRecord().getKey());
  }

  /**
   * Will trigger the creation of a new instance of the flow with the same elementId as the event,
   * but in an asynchronous fashion in the form of a deferred record.
   */
  protected long deferEvent(
      BpmnStepContext<T> context, EventTrigger event, ExecutableFlowElement element) {
    final long eventInstanceKey =
        context
            .getOutput()
            .deferRecord(
                context.getRecord().getKey(),
                getEventRecord(context, event, element),
                WorkflowInstanceIntent.ELEMENT_READY);

    handleEvent(context, eventInstanceKey, event);
    return eventInstanceKey;
  }

  /**
   * Will trigger the creation of a new instance of the flow with the same elementId as the event in
   * a synchronous fashion by publishing its ELEMENT_READY event and spawning a new token for it.
   */
  protected long publishEvent(
      BpmnStepContext<T> context, EventTrigger event, ExecutableFlowElement element) {
    final long eventInstanceKey =
        context
            .getOutput()
            .appendNewEvent(
                WorkflowInstanceIntent.ELEMENT_READY, getEventRecord(context, event, element));
    handleEvent(context, eventInstanceKey, event);
    context.getFlowScopeInstance().spawnToken();

    return eventInstanceKey;
  }

  /**
   * Applies the event payload to the given variable scope, and removes the event from the state
   * ensuring it cannot be reprocessed.
   */
  protected void handleEvent(
      BpmnStepContext<T> context, long variableScopeKey, EventTrigger event) {
    context
        .getElementInstanceState()
        .getVariablesState()
        .setPayload(variableScopeKey, event.getPayload());

    context
        .getStateDb()
        .getEventScopeInstanceState()
        .deleteTrigger(context.getRecord().getKey(), event.getEventKey());
  }

  private WorkflowInstanceRecord getEventRecord(
      BpmnStepContext<T> context, EventTrigger event, ExecutableFlowElement element) {
    eventRecord.reset();
    eventRecord.wrap(context.getValue());
    eventRecord.setElementId(event.getElementId());
    eventRecord.setPayload(event.getPayload());
    eventRecord.setBpmnElementType(element.getElementType());

    return eventRecord;
  }

  /**
   * Timer/Message start events publish an EVENT_OCCURRED event to their respective flow elements,
   * but these are not initially part of a workflow instance.
   */
  private boolean hasWorkflowInstance(BpmnStepContext<T> context) {
    return context.getRecord().getValue().getWorkflowInstanceKey() >= 0;
  }
}
