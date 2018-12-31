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
package io.zeebe.broker.workflow.processor.deployment;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.message.data.MessageStartEventSubscriptionRecord;
import io.zeebe.broker.workflow.model.element.ExecutableCatchEventElement;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import java.util.List;

public class DeploymentCreatedProcessor implements TypedRecordProcessor<DeploymentRecord> {

  private WorkflowState workflowState;

  public DeploymentCreatedProcessor(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    final DeploymentRecord deploymentEvent = event.getValue();

    // TODO: Write Distribute only on deployment partition (partition 0)
    streamWriter.appendFollowUpCommand(
        event.getKey(), DeploymentIntent.DISTRIBUTE, deploymentEvent);

    for (final Workflow workflowRecord : deploymentEvent.workflows()) {
      if (workflowRecord.getVersion() != 1) { // if not the first version
        closeExistingMessageStartEventSubscriptions(workflowState, workflowRecord, streamWriter);
      }
      openMessageStartEventSubscriptions(workflowState, workflowRecord, streamWriter);
    }
  }

  private void closeExistingMessageStartEventSubscriptions(
      WorkflowState workflowState, Workflow workflowRecord, TypedStreamWriter streamWriter) {
    final int previousVersion = workflowRecord.getVersion() - 1;
    final long previousWorkflowKey =
        workflowState
            .getWorkflowByProcessIdAndVersion(workflowRecord.getBpmnProcessId(), previousVersion)
            .getKey();
    final MessageStartEventSubscriptionRecord subscriptionRecord =
        new MessageStartEventSubscriptionRecord();
    subscriptionRecord.setWorkflowKey(previousWorkflowKey);

    streamWriter.appendNewCommand(MessageStartEventSubscriptionIntent.CLOSE, subscriptionRecord);
  }

  private void openMessageStartEventSubscriptions(
      WorkflowState workflowState, Workflow workflowRecord, TypedStreamWriter streamWriter) {
    final long workflowKey = workflowRecord.getKey();
    final DeployedWorkflow workflowDefinition = workflowState.getWorkflowByKey(workflowKey);
    final ExecutableWorkflow workflow = workflowDefinition.getWorkflow();
    final List<ExecutableCatchEventElement> startEvents = workflow.getStartEvents();

    // if startEvents contain message events
    for (ExecutableCatchEventElement startEvent : startEvents) {
      if (startEvent.isMessage()) {
        final MessageStartEventSubscriptionRecord subscriptionRecord =
            new MessageStartEventSubscriptionRecord();
        subscriptionRecord
            .setMessageName(startEvent.getMessage().getMessageName())
            .setWorkflowKey(workflowKey)
            .setStartEventId(startEvent.getId());
        streamWriter.appendNewCommand(MessageStartEventSubscriptionIntent.OPEN, subscriptionRecord);
      }
    }
  }
}
