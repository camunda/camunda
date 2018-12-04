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

import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.workflow.state.WorkflowInstanceSubscription;
import io.zeebe.util.sched.clock.ActorClock;

public class PendingWorkflowInstanceSubscriptionChecker implements Runnable {

  private final SubscriptionCommandSender commandSender;
  private final WorkflowInstanceSubscriptionState subscriptionState;

  private final long subscriptionTimeout;

  public PendingWorkflowInstanceSubscriptionChecker(
      SubscriptionCommandSender commandSender,
      WorkflowInstanceSubscriptionState subscriptionState,
      long subscriptionTimeout) {
    this.commandSender = commandSender;
    this.subscriptionState = subscriptionState;
    this.subscriptionTimeout = subscriptionTimeout;
  }

  @Override
  public void run() {

    subscriptionState.visitSubscriptionBefore(
        ActorClock.currentTimeMillis() - subscriptionTimeout, this::sendCommand);
  }

  private boolean sendCommand(WorkflowInstanceSubscription subscription) {
    boolean success = false;

    if (subscription.isOpening()) {
      success = sendOpenCommand(subscription);
    } else {
      success = sendCloseCommand(subscription);
    }

    if (success) {
      subscriptionState.updateSentTime(subscription, ActorClock.currentTimeMillis());
    }

    return success;
  }

  private boolean sendOpenCommand(WorkflowInstanceSubscription subscription) {
    return commandSender.openMessageSubscription(
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getMessageName(),
        subscription.getCorrelationKey());
  }

  private boolean sendCloseCommand(WorkflowInstanceSubscription subscription) {
    return commandSender.closeMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getMessageName());
  }
}
