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

import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.MessageState;
import io.zeebe.broker.subscription.message.state.MessageSubscription;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.List;

public class PendingMessageSubscriptionChecker implements Runnable {

  private final SubscriptionCommandSender commandSender;
  private final MessageState messageState;

  private final long subscriptionTimeout;

  public PendingMessageSubscriptionChecker(
      SubscriptionCommandSender commandSender,
      MessageState messageState,
      long subscriptionTimeout) {
    this.commandSender = commandSender;
    this.messageState = messageState;
    this.subscriptionTimeout = subscriptionTimeout;
  }

  @Override
  public void run() {

    final List<MessageSubscription> pendingSubscriptions =
        messageState.findSubscriptionBefore(ActorClock.currentTimeMillis() - subscriptionTimeout);

    for (MessageSubscription subscription : pendingSubscriptions) {
      if (sendCommand(subscription)) {
        messageState.updateCommandSentTime(subscription);
      } else {
        return;
      }
    }
  }

  private boolean sendCommand(MessageSubscription subscription) {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getMessageName(),
        subscription.getMessagePayload());
  }
}
