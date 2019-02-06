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
package io.zeebe.broker.workflow.processor.handlers.container;

import io.zeebe.broker.workflow.model.element.ExecutableCatchEventElement;
import io.zeebe.broker.workflow.model.element.ExecutableFlowElementContainer;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.element.ElementActivatedHandler;
import io.zeebe.broker.workflow.state.IndexedRecord;
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;

public class ContainerElementActivatedHandler<T extends ExecutableFlowElementContainer>
    extends ElementActivatedHandler<T> {
  private final WorkflowState workflowState;

  public ContainerElementActivatedHandler(WorkflowState workflowState) {
    this(null, workflowState);
  }

  public ContainerElementActivatedHandler(
      WorkflowInstanceIntent nextState, WorkflowState workflowState) {
    super(nextState);
    this.workflowState = workflowState;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final ExecutableFlowElementContainer element = context.getElement();
    final long scopeInstanceKey = context.getRecord().getKey();
    final WorkflowInstanceRecord value = context.getValue();
    final ExecutableCatchEventElement firstStartEvent = element.getStartEvents().get(0);
    final long eventInstanceKey;

    if (firstStartEvent.isNone()) {
      value.setElementId(firstStartEvent.getId());
      value.setBpmnElementType(firstStartEvent.getElementType());
    } else {
      populateRecordFromDeferredRecord(context, value);
    }
    value.setScopeInstanceKey(scopeInstanceKey);
    context.getElementInstance().spawnToken();

    eventInstanceKey =
        context.getOutput().appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, value);
    context
        .getElementInstanceState()
        .getVariablesState()
        .setPayload(eventInstanceKey, value.getPayload());

    return true;
  }

  private void populateRecordFromDeferredRecord(
      BpmnStepContext<T> context, WorkflowInstanceRecord value) {
    final long wfInstanceKey = context.getRecord().getValue().getWorkflowInstanceKey();
    final List<IndexedRecord> deferredRecords =
        workflowState.getElementInstanceState().getDeferredRecords(wfInstanceKey);

    if (deferredRecords.isEmpty()) {
      throw new IllegalStateException(
          "Expected workflow with multiple start events to have a deferred record, but no such token was found");
    }

    assert deferredRecords.size() == 1
        : "should only have one deferred start event per workflow instance";

    final IndexedRecord deferredRecord = deferredRecords.get(0);
    final WorkflowInstanceRecord workflowInstanceRecord = deferredRecord.getValue();
    value.setElementId(workflowInstanceRecord.getElementId());
    value.setBpmnElementType(workflowInstanceRecord.getBpmnElementType());
    value.setPayload(workflowInstanceRecord.getPayload());
    workflowState
        .getElementInstanceState()
        .removeStoredRecord(wfInstanceKey, deferredRecord.getKey(), Purpose.DEFERRED);
  }
}
