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

import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.state.Message;
import io.zeebe.broker.subscription.message.state.MessageState;
import io.zeebe.broker.subscription.message.state.MessageSubscription;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionState;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MessageCorrelator {

  private final DirectBuffer messagePayload = new UnsafeBuffer();
  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;

  public MessageCorrelator(
      MessageState messageState,
      MessageSubscriptionState subscriptionState,
      SubscriptionCommandSender commandSender) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
  }

  private Consumer<SideEffectProducer> sideEffect;
  private MessageSubscriptionRecord subscriptionRecord;
  private MessageSubscription subscription;

  public void correlateNextMessage(
      MessageSubscription subscription,
      MessageSubscriptionRecord subscriptionRecord,
      Consumer<SideEffectProducer> sideEffect) {
    this.subscription = subscription;
    this.subscriptionRecord = subscriptionRecord;
    this.sideEffect = sideEffect;

    messageState.visitMessages(
        subscription.getMessageName(), subscription.getCorrelationKey(), this::correlateMessage);
  }

  private boolean correlateMessage(final Message message) {
    // correlate the first message which is not correlated to the workflow instance yet

    final boolean isCorrelatedBefore =
        messageState.existMessageCorrelation(
            message.getKey(), subscriptionRecord.getWorkflowInstanceKey());

    if (!isCorrelatedBefore) {
      subscriptionState.updateToCorrelatingState(
          subscription, message.getPayload(), ActorClock.currentTimeMillis());

      // send the correlate instead of acknowledge command
      messagePayload.wrap(message.getPayload());
      sideEffect.accept(this::sendCorrelateCommand);

      messageState.putMessageCorrelation(
          message.getKey(), subscriptionRecord.getWorkflowInstanceKey());
    }

    return isCorrelatedBefore;
  }

  private boolean sendCorrelateCommand() {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageName(),
        messagePayload);
  }
}
