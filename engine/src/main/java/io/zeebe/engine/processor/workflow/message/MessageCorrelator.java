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
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MessageCorrelator {

  private final DirectBuffer messageVariables = new UnsafeBuffer();
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
  private long messageKey;

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
    messageKey = message.getKey();
    final boolean isCorrelatedBefore =
        messageState.existMessageCorrelation(
            messageKey, subscriptionRecord.getWorkflowInstanceKey());

    if (!isCorrelatedBefore) {
      subscriptionState.updateToCorrelatingState(
          subscription, message.getVariables(), ActorClock.currentTimeMillis(), messageKey);

      // send the correlate instead of acknowledge command
      messageVariables.wrap(message.getVariables());
      sideEffect.accept(this::sendCorrelateCommand);

      messageState.putMessageCorrelation(messageKey, subscriptionRecord.getWorkflowInstanceKey());
    }

    return isCorrelatedBefore;
  }

  private boolean sendCorrelateCommand() {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageName(),
        messageKey,
        messageVariables);
  }
}
