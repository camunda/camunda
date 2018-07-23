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

import static io.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.MessageRecord;
import io.zeebe.broker.subscription.message.state.MessageDataStore;
import io.zeebe.broker.subscription.message.state.MessageDataStore.Message;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionDataStore;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionDataStore.MessageSubscription;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.MessageIntent;
import java.util.List;
import java.util.function.Consumer;

public class PublishMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private final MessageDataStore messageStore;
  private final MessageSubscriptionDataStore subscriptionStore;

  private SubscriptionCommandSender commandSender;

  private TypedResponseWriter responseWriter;
  private MessageRecord messageRecord;
  private List<MessageSubscription> matchingSubscriptions;

  public PublishMessageProcessor(
      MessageDataStore messageStore, MessageSubscriptionDataStore subscriptionStore) {
    this.messageStore = messageStore;
    this.subscriptionStore = subscriptionStore;
  }

  @Override
  public void processRecord(
      TypedRecord<MessageRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {
    this.responseWriter = responseWriter;

    messageRecord = record.getValue();

    final Message message =
        new Message(
            bufferAsString(messageRecord.getName()),
            bufferAsString(messageRecord.getCorrelationKey()),
            bufferAsArray(messageRecord.getPayload()),
            messageRecord.hasMessageId() ? bufferAsString(messageRecord.getMessageId()) : null);

    if (messageRecord.hasMessageId() && messageStore.hasMessage(message)) {
      final String rejectionReason =
          String.format(
              "message with id '%s' is already published",
              bufferAsString(messageRecord.getMessageId()));

      streamWriter.writeRejection(record, RejectionType.BAD_VALUE, rejectionReason);
      responseWriter.writeRejectionOnCommand(record, RejectionType.BAD_VALUE, rejectionReason);

    } else {
      final long key = streamWriter.writeNewEvent(MessageIntent.PUBLISHED, record.getValue());
      responseWriter.writeEventOnCommand(key, MessageIntent.PUBLISHED, record);

      matchingSubscriptions =
          subscriptionStore.findSubscriptions(message.getName(), message.getCorrelationKey());

      sideEffect.accept(this::correlateMessage);

      messageStore.addMessage(message);
    }
  }

  private boolean correlateMessage() {
    for (MessageSubscription sub : matchingSubscriptions) {
      final boolean success =
          commandSender.correlateWorkflowInstanceSubscription(
              sub.getWorkflowInstancePartitionId(),
              sub.getWorkflowInstanceKey(),
              sub.getActivityInstanceKey(),
              messageRecord.getName(),
              messageRecord.getPayload());

      if (!success) {
        // try again
        return false;
      }
    }

    return responseWriter.flush();
  }

  public void setSubscriptionCommandSender(SubscriptionCommandSender sender) {
    this.commandSender = sender;
  }
}
