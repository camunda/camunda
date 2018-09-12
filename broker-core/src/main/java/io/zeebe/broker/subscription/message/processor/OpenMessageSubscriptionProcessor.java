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
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.state.Message;
import io.zeebe.broker.subscription.message.state.MessageStateController;
import io.zeebe.broker.subscription.message.state.MessageSubscription;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class OpenMessageSubscriptionProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final MessageStateController messageStateController;
  private final SubscriptionCommandSender commandSender;

  private final DirectBuffer messagePayload = new UnsafeBuffer(0, 0);

  private MessageSubscriptionRecord subscriptionRecord;

  public OpenMessageSubscriptionProcessor(
      MessageStateController messageStateController, SubscriptionCommandSender commandSender) {
    this.messageStateController = messageStateController;
    this.commandSender = commandSender;
  }

  @Override
  public void processRecord(
      TypedRecord<MessageSubscriptionRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    subscriptionRecord = record.getValue();

    final MessageSubscription subscription =
        new MessageSubscription(
            subscriptionRecord.getWorkflowInstancePartitionId(),
            subscriptionRecord.getWorkflowInstanceKey(),
            subscriptionRecord.getActivityInstanceKey(),
            subscriptionRecord.getMessageName(),
            subscriptionRecord.getCorrelationKey());

    if (messageStateController.exist(subscription)) {
      sideEffect.accept(this::sendAcknowledgeCommand);

      streamWriter.writeRejection(
          record, RejectionType.NOT_APPLICABLE, "subscription is already open");
      return;
    }

    // handle new subscription
    handleNewSubscription(record, streamWriter, sideEffect, subscription);
  }

  private void handleNewSubscription(
      TypedRecord<MessageSubscriptionRecord> record,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      MessageSubscription subscription) {
    final Message message =
        messageStateController.findMessage(
            subscriptionRecord.getMessageName(), subscriptionRecord.getCorrelationKey());

    if (message != null) {
      messagePayload.wrap(message.getPayload());

      sideEffect.accept(this::sendCorrelateCommand);

      subscription.setMessagePayload(message.getPayload());
      subscription.setCommandSentTime(ActorClock.currentTimeMillis());
    } else {
      sideEffect.accept(this::sendAcknowledgeCommand);
    }
    messageStateController.put(subscription);

    streamWriter.writeFollowUpEvent(
        record.getKey(), MessageSubscriptionIntent.OPENED, subscriptionRecord);
  }

  private boolean sendCorrelateCommand() {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstancePartitionId(),
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getActivityInstanceKey(),
        subscriptionRecord.getMessageName(),
        messagePayload);
  }

  private boolean sendAcknowledgeCommand() {
    return commandSender.openWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstancePartitionId(),
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getActivityInstanceKey(),
        subscriptionRecord.getMessageName());
  }
}
