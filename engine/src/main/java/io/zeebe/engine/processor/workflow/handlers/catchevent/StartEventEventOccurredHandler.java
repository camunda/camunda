/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.handlers.catchevent;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class StartEventEventOccurredHandler<T extends ExecutableCatchEventElement>
    extends EventOccurredHandler<T> {
  private static final String NO_WORKFLOW_FOUND_MESSAGE =
      "Expected to create an instance of workflow with key '%d', but no such workflow was found";
  private static final String NO_TRIGGERED_EVENT_MESSAGE = "No triggered event for workflow '%d'";

  private final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
  private final WorkflowState state;
  private final KeyGenerator keyGenerator;

  public StartEventEventOccurredHandler(ZeebeState zeebeState) {
    this(null, zeebeState);
  }

  public StartEventEventOccurredHandler(WorkflowInstanceIntent nextState, ZeebeState zeebeState) {
    super(nextState);
    this.state = zeebeState.getWorkflowState();
    keyGenerator = zeebeState.getKeyGenerator();
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final WorkflowInstanceRecord event = context.getRecord().getValue();
    final long workflowKey = event.getWorkflowKey();
    final DeployedWorkflow workflow = state.getWorkflowByKey(workflowKey);
    final long workflowInstanceKey = keyGenerator.nextKey();

    // this should never happen because workflows are never deleted.
    if (workflow == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
          String.format(NO_WORKFLOW_FOUND_MESSAGE, workflowKey));
      return false;
    }

    final EventTrigger triggeredEvent = getTriggeredEvent(context, workflowKey);
    if (triggeredEvent == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
          String.format(NO_TRIGGERED_EVENT_MESSAGE, workflowKey));
      return false;
    }

    createWorkflowInstance(context, workflow, workflowInstanceKey);
    final WorkflowInstanceRecord record =
        getEventRecord(context, triggeredEvent, BpmnElementType.START_EVENT)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setVersion(workflow.getVersion())
            .setBpmnProcessId(workflow.getBpmnProcessId())
            .setFlowScopeKey(workflowInstanceKey);

    deferEvent(context, workflowKey, workflowInstanceKey, record, triggeredEvent);
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
    event.setFlowScopeKey(workflowInstanceKey);

    context
        .getOutput()
        .deferRecord(workflowInstanceKey, event, WorkflowInstanceIntent.ELEMENT_ACTIVATING);
  }
}
