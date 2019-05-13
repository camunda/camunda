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
package io.zeebe.engine.processor.workflow.deployment;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import java.util.List;

public class DeploymentCreatedProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private final WorkflowState workflowState;
  private final boolean isDeploymentPartition;
  private final MessageStartEventSubscriptionRecord subscriptionRecord =
      new MessageStartEventSubscriptionRecord();

  public DeploymentCreatedProcessor(WorkflowState workflowState, boolean isDeploymentPartition) {
    this.workflowState = workflowState;
    this.isDeploymentPartition = isDeploymentPartition;
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    final DeploymentRecord deploymentEvent = event.getValue();

    if (isDeploymentPartition) {
      streamWriter.appendFollowUpCommand(
          event.getKey(), DeploymentIntent.DISTRIBUTE, deploymentEvent);
    }

    for (final Workflow workflowRecord : deploymentEvent.workflows()) {
      if (isLatestWorkflow(workflowRecord)) {
        closeExistingMessageStartEventSubscriptions(workflowRecord, streamWriter);
        openMessageStartEventSubscriptions(workflowRecord, streamWriter);
      }
    }
  }

  private boolean isLatestWorkflow(Workflow workflow) {
    return workflowState
            .getLatestWorkflowVersionByProcessId(workflow.getBpmnProcessId())
            .getVersion()
        == workflow.getVersion();
  }

  private void closeExistingMessageStartEventSubscriptions(
      Workflow workflowRecord, TypedStreamWriter streamWriter) {
    final DeployedWorkflow lastMsgWorkflow = findLastMessageStartWorkflow(workflowRecord);
    if (lastMsgWorkflow == null) {
      return;
    }

    subscriptionRecord.reset();
    subscriptionRecord.setWorkflowKey(lastMsgWorkflow.getKey());
    streamWriter.appendNewCommand(MessageStartEventSubscriptionIntent.CLOSE, subscriptionRecord);
  }

  private DeployedWorkflow findLastMessageStartWorkflow(final Workflow workflowRecord) {
    for (int version = workflowRecord.getVersion() - 1; version > 0; --version) {
      final DeployedWorkflow lastMsgWorkflow =
          workflowState.getWorkflowByProcessIdAndVersion(
              workflowRecord.getBpmnProcessId(), version);
      if (lastMsgWorkflow != null
          && lastMsgWorkflow.getWorkflow().getStartEvents().stream().anyMatch(e -> e.isMessage())) {
        return lastMsgWorkflow;
      }
    }

    return null;
  }

  private void openMessageStartEventSubscriptions(
      Workflow workflowRecord, TypedStreamWriter streamWriter) {
    final long workflowKey = workflowRecord.getKey();
    final DeployedWorkflow workflowDefinition = workflowState.getWorkflowByKey(workflowKey);
    final ExecutableWorkflow workflow = workflowDefinition.getWorkflow();
    final List<ExecutableCatchEventElement> startEvents = workflow.getStartEvents();

    // if startEvents contain message events
    for (ExecutableCatchEventElement startEvent : startEvents) {
      if (startEvent.isMessage()) {
        subscriptionRecord.reset();
        subscriptionRecord
            .setMessageName(startEvent.getMessage().getMessageName())
            .setWorkflowKey(workflowKey)
            .setStartEventId(startEvent.getId());
        streamWriter.appendNewCommand(MessageStartEventSubscriptionIntent.OPEN, subscriptionRecord);
      }
    }
  }
}
