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
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.flownode.TerminateElementHandler;
import io.zeebe.broker.workflow.state.WorkflowInstanceSubscription;
import io.zeebe.util.sched.clock.ActorClock;

public class TerminateIntermediateMessageHandler extends TerminateElementHandler {

  private final WorkflowInstanceSubscriptionState subscriptionState;
  private final SubscriptionCommandSender subscriptionCommandSender;

  private WorkflowInstanceSubscription subscription;

  public TerminateIntermediateMessageHandler(
      WorkflowInstanceSubscriptionState subscriptionState,
      SubscriptionCommandSender subscriptionCommandSender) {
    this.subscriptionState = subscriptionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
  }

  @Override
  protected void addTerminatingRecords(
      BpmnStepContext<ExecutableFlowNode> context, TypedBatchWriter batch) {

    final long elementInstanceKey = context.getElementInstance().getKey();

    subscription = subscriptionState.getSubscription(elementInstanceKey);

    if (subscription != null) {
      context.getSideEffect().accept(this::sendSubscriptionCommand);

      subscriptionState.updateToClosingState(subscription, ActorClock.currentTimeMillis());
    }
  }

  private boolean sendSubscriptionCommand() {
    return subscriptionCommandSender.closeMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey());
  }
}
