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
package io.zeebe.broker.workflow.processor.subprocess;

import io.zeebe.broker.workflow.model.element.ExecutableFlowElementContainer;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.state.IndexedRecord;
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;

public class TriggerStartEventHandler implements BpmnStepHandler<ExecutableFlowElementContainer> {

  private WorkflowState workflowState;

  public TriggerStartEventHandler(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableFlowElementContainer> context) {
    final ExecutableFlowElementContainer element = context.getElement();
    final long scopeInstanceKey = context.getRecord().getKey();

    final WorkflowInstanceRecord value = context.getValue();

    if (element.getStartEvents().get(0).isNone()) {
      // if none start event
      value.setElementId(element.getStartEvents().get(0).getId());
    } else {
      // if timer/message start event

      final long wfInstanceKey = context.getRecord().getValue().getWorkflowInstanceKey();
      final List<IndexedRecord> deferredRecords =
          workflowState.getElementInstanceState().getDeferredRecords(wfInstanceKey);

      if (deferredRecords.size() > 0) {
        final IndexedRecord deferredRecord = deferredRecords.get(0);
        final WorkflowInstanceRecord workflowInstanceRecord = deferredRecord.getValue();
        value.setElementId(workflowInstanceRecord.getElementId());
        value.setPayload(workflowInstanceRecord.getPayload());
        workflowState
            .getElementInstanceState()
            .removeStoredRecord(wfInstanceKey, deferredRecord.getKey(), Purpose.DEFERRED);
      } else {
        throw new RuntimeException(
            "Workflow has multiple start events but no deferred record was found");
      }
    }

    value.setScopeInstanceKey(scopeInstanceKey);

    context.getOutput().appendNewEvent(WorkflowInstanceIntent.EVENT_TRIGGERING, value);

    // spawn a new token to continue at the start event
    context.getElementInstanceState().spawnToken(scopeInstanceKey);
  }
}
