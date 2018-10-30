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
package io.zeebe.broker.workflow.processor.boundary;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.workflow.model.element.AbstractFlowElement;
import io.zeebe.broker.workflow.model.element.ExecutableBoundaryEvent;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public class BoundaryEventHelper {
  private final WorkflowInstanceRecord newRecord = new WorkflowInstanceRecord();

  /**
   * Will start the lifecycle of the boundary event, by publishing ELEMENT_READY to the stream as a
   * new record, and creating an {@link io.zeebe.broker.workflow.state.ElementInstanceState} entry.
   *
   * <p>Before that, however, if the boundary event definition specifies the attached to activity
   * should be cancelled, it will also publish an ELEMENT_TERMINATING event to the log stream,
   * update the associated element instance state.
   */
  public void triggerBoundaryEvent(
      WorkflowState state,
      ElementInstance attachedTo,
      DirectBuffer handlerNodeId,
      TypedBatchWriter writer) {
    newRecord.wrap(attachedTo.getValue());
    newRecord.setElementId(handlerNodeId);

    if (shouldTerminateAttachedActivity(state, attachedTo, handlerNodeId)) {
      terminateAttachedToActivity(attachedTo, writer);
    }

    writer.addNewEvent(WorkflowInstanceIntent.BOUNDARY_EVENT_TRIGGERED, newRecord);
  }

  /**
   * Returns true if the activity the boundary event trigger is attached to is activated, and the
   * handler node ID is not the same as the attached activity's ID.
   */
  public boolean shouldTriggerBoundaryEvent(
      ElementInstance attachedTo, DirectBuffer handlerNodeId) {
    return attachedTo.getState() == WorkflowInstanceIntent.ELEMENT_ACTIVATED
        && !attachedTo.getValue().getElementId().equals(handlerNodeId);
  }

  private void terminateAttachedToActivity(ElementInstance attachedTo, TypedBatchWriter writer) {
    writer.addFollowUpEvent(
        attachedTo.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, attachedTo.getValue());
    attachedTo.setState(WorkflowInstanceIntent.ELEMENT_TERMINATING);
  }

  private boolean shouldTerminateAttachedActivity(
      WorkflowState state, ElementInstance attachedTo, DirectBuffer boundaryId) {
    return getBoundaryEventById(state, attachedTo.getValue().getWorkflowKey(), boundaryId)
        .cancelActivity();
  }

  private ExecutableBoundaryEvent getBoundaryEventById(
      WorkflowState state, long workflowKey, DirectBuffer id) {
    final DeployedWorkflow workflow = state.getWorkflowByKey(workflowKey);
    if (workflow == null) {
      throw new IllegalStateException(
          "Error fetching boundary event definition; workflow with "
              + workflowKey
              + " is not deployed");
    }

    final AbstractFlowElement element = workflow.getWorkflow().getElementById(id);
    if (!ExecutableBoundaryEvent.class.isAssignableFrom(element.getClass())) {
      throw new IllegalStateException(
          "Element with ID " + bufferAsString(id) + " is not a boundary event");
    }

    return (ExecutableBoundaryEvent) element;
  }
}
