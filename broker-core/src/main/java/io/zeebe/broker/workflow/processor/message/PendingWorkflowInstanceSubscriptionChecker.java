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
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.broker.workflow.state.WorkflowSubscription;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.List;

public class PendingWorkflowInstanceSubscriptionChecker implements Runnable {

  private final SubscriptionCommandSender commandSender;
  private final WorkflowState workflowState;

  private final long subscriptionTimeout;

  public PendingWorkflowInstanceSubscriptionChecker(
      SubscriptionCommandSender commandSender,
      WorkflowState workflowState,
      long subscriptionTimeout) {
    this.commandSender = commandSender;
    this.workflowState = workflowState;
    this.subscriptionTimeout = subscriptionTimeout;
  }

  @Override
  public void run() {

    final List<WorkflowSubscription> pendingSubscriptions =
        workflowState.findSubscriptionsBefore(ActorClock.currentTimeMillis() - subscriptionTimeout);

    for (WorkflowSubscription subscription : pendingSubscriptions) {
      boolean success = true;

      if (subscription.isOpening()) {
        success = sendOpenCommand(subscription);
        workflowState.updateCommandSendTime(subscription);

      } else if (subscription.isClosing()) {
        success = sendCloseCommand(subscription);
        workflowState.updateCommandSendTime(subscription);
      }

      if (!success) {
        return;
      }
    }
  }

  private boolean sendOpenCommand(WorkflowSubscription subscription) {
    return commandSender.openMessageSubscription(
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getMessageName(),
        subscription.getCorrelationKey());
  }

  private boolean sendCloseCommand(WorkflowSubscription subscription) {
    return commandSender.closeMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey());
  }
}
