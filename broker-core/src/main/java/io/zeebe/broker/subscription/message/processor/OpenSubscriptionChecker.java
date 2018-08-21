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
package io.zeebe.broker.subscription.message.processor;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore.WorkflowInstanceSubscription;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.List;

public class OpenSubscriptionChecker implements Runnable {

  private final SubscriptionCommandSender commandSender;
  private final WorkflowInstanceSubscriptionDataStore subscriptionStore;

  private final long subscriptionTimeout;

  public OpenSubscriptionChecker(
      SubscriptionCommandSender commandSender,
      WorkflowInstanceSubscriptionDataStore subscriptionStore,
      long subscriptionTimeout) {
    this.commandSender = commandSender;
    this.subscriptionStore = subscriptionStore;
    this.subscriptionTimeout = subscriptionTimeout;
  }

  @Override
  public void run() {

    final List<WorkflowInstanceSubscription> pendingSubscriptions =
        subscriptionStore.findSubscriptionWithSentTimeBefore(
            ActorClock.currentTimeMillis() - subscriptionTimeout);

    for (WorkflowInstanceSubscription subscription : pendingSubscriptions) {
      final boolean success = sendCommand(subscription);
      if (!success) {
        return;
      }
    }
  }

  private boolean sendCommand(WorkflowInstanceSubscription subscription) {
    subscription.setSentTime(ActorClock.currentTimeMillis());

    return commandSender.openMessageSubscription(
        subscription.getWorkflowInstanceKey(),
        subscription.getActivityInstanceKey(),
        wrapString(subscription.getMessageName()),
        wrapString(subscription.getCorrelationKey()));
  }
}
