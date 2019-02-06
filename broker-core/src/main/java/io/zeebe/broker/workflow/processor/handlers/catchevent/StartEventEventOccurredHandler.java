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
package io.zeebe.broker.workflow.processor.handlers.catchevent;

import io.zeebe.broker.workflow.model.element.ExecutableCatchEventElement;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.element.EventOccurredHandler;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class StartEventEventOccurredHandler<T extends ExecutableCatchEventElement>
    extends EventOccurredHandler<T> {
  private static final String NO_WORKFLOW_FOUND_MESSAGE =
      "Expected to create an instance of workflow with key '%d', but no such workflow was found";

  private final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
  private final WorkflowState state;

  public StartEventEventOccurredHandler(WorkflowState state) {
    this(null, state);
  }

  public StartEventEventOccurredHandler(WorkflowInstanceIntent nextState, WorkflowState state) {
    super(nextState);
    this.state = state;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    // todo: how is event payload passed to workflow from this?

    final WorkflowInstanceRecord event = context.getRecord().getValue();
    final long workflowKey = event.getWorkflowKey();
    final DeployedWorkflow workflow = state.getWorkflowByKey(workflowKey);
    final long workflowInstanceKey =
        context.getOutput().getStreamWriter().getKeyGenerator().nextKey();

    // this should never happen because workflows are never deleted.
    if (workflow == null) {
      throw new IllegalStateException(String.format(NO_WORKFLOW_FOUND_MESSAGE, workflowKey));
    }

    createWorkflowInstance(context, workflow, workflowInstanceKey);
    deferStartEventRecord(context, workflowInstanceKey, event);

    return true;
  }

  private void createWorkflowInstance(
      BpmnStepContext<T> context, DeployedWorkflow workflow, long workflowInstanceKey) {
    record
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setWorkflowKey(workflow.getKey())
        .setVersion(workflow.getVersion())
        .setWorkflowInstanceKey(workflowInstanceKey);

    context
        .getOutput()
        .appendFollowUpEvent(
            workflowInstanceKey,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            record,
            workflow.getWorkflow());
  }

  private void deferStartEventRecord(
      BpmnStepContext<T> context, long workflowInstanceKey, WorkflowInstanceRecord event) {
    event.setWorkflowInstanceKey(workflowInstanceKey);
    event.setScopeInstanceKey(workflowInstanceKey);

    context
        .getOutput()
        .deferRecord(workflowInstanceKey, event, WorkflowInstanceIntent.ELEMENT_ACTIVATING);
  }
}
