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
package io.zeebe.broker.workflow.processor.message;

import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.flownode.TerminateElementHandler;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.broker.workflow.state.WorkflowSubscription;

public class TerminateIntermediateMessageHandler extends TerminateElementHandler {

  private final WorkflowState workflowState;
  private final SubscriptionCommandSender subscriptionCommandSender;

  private WorkflowSubscription subscription;

  public TerminateIntermediateMessageHandler(
      WorkflowState workflowState, SubscriptionCommandSender subscriptionCommandSender) {
    this.workflowState = workflowState;
    this.subscriptionCommandSender = subscriptionCommandSender;
  }

  @Override
  protected void addTerminatingRecords(
      BpmnStepContext<ExecutableFlowNode> context, TypedBatchWriter batch) {

    final long elementInstanceKey = context.getElementInstance().getKey();
    final long workflowInstanceKey = context.getValue().getWorkflowInstanceKey();

    subscription = workflowState.findSubscription(workflowInstanceKey, elementInstanceKey);

    context.getSideEffect().accept(this::sendSubscriptionCommand);

    subscription.setClosing();
    workflowState.updateCommandSendTime(subscription);
  }

  private boolean sendSubscriptionCommand() {
    return subscriptionCommandSender.closeMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey());
  }
}
