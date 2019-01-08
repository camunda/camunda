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
package io.zeebe.broker.workflow.processor.instance;

import io.zeebe.broker.incident.processor.TypedWorkflowInstanceRecord;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.workflow.model.element.ExecutableFlowElementContainer;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.processor.EventOutput;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.IndexedRecord;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public class CreateWorkflowInstanceOnStartEventHandler
    implements BpmnStepHandler<ExecutableFlowElementContainer> {

  private final ZeebeState state;
  private final WorkflowState workflowState;

  public CreateWorkflowInstanceOnStartEventHandler(ZeebeState zeebeState) {
    this.state = zeebeState;
    this.workflowState = zeebeState.getWorkflowState();
  }

  @Override
  public void handle(BpmnStepContext<ExecutableFlowElementContainer> context) {

    final WorkflowInstanceRecord eventRecord = context.getRecord().getValue();

    final long workflowKey = eventRecord.getWorkflowKey();
    final DeployedWorkflow workflowDefinition = workflowState.getWorkflowByKey(workflowKey);

    if (workflowDefinition != null) {
      final long workflowInstanceKey = state.getKeyGenerator().nextKey();
      final DirectBuffer bpmnId = workflowDefinition.getWorkflow().getId();
      final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
      record
          .setBpmnProcessId(bpmnId)
          .setWorkflowKey(workflowDefinition.getKey())
          .setVersion(workflowDefinition.getVersion())
          .setElementId(bpmnId)
          .setWorkflowInstanceKey(workflowInstanceKey);

      final EventOutput eventOutput = context.getOutput();
      eventOutput.appendFollowUpEvent(
          workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_READY, record);

      // Defer token which will be used by the start event
      eventRecord.setWorkflowInstanceKey(workflowInstanceKey);
      eventRecord.setScopeInstanceKey(workflowInstanceKey);

      final IndexedRecord indexedRecord =
          new IndexedRecord(
              workflowInstanceKey, WorkflowInstanceIntent.EVENT_TRIGGERING, eventRecord);
      final TypedWorkflowInstanceRecord deferredEvent = new TypedWorkflowInstanceRecord();
      deferredEvent.wrap(indexedRecord);

      eventOutput.deferEvent(deferredEvent);

    } else {
      // this should never happen because workflows are never deleted.
      throw new IllegalStateException(
          String.format(
              "Expected to find deployed workflow with key %d, but no workflow found",
              workflowKey));
    }
  }
}
