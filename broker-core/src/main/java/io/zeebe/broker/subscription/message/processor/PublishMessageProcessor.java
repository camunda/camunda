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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.MessageRecord;
import io.zeebe.broker.subscription.message.state.Message;
import io.zeebe.broker.subscription.message.state.MessageStateController;
import io.zeebe.broker.subscription.message.state.MessageSubscription;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.List;
import java.util.function.Consumer;

public class PublishMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private final MessageStateController messageStateController;
  private final SubscriptionCommandSender commandSender;

  private TypedResponseWriter responseWriter;
  private MessageRecord messageRecord;
  private List<MessageSubscription> matchingSubscriptions;

  public PublishMessageProcessor(
      MessageStateController messageStateController,
      final SubscriptionCommandSender commandSender) {
    this.messageStateController = messageStateController;
    this.commandSender = commandSender;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    this.responseWriter = responseWriter;
    messageRecord = record.getValue();

    final Message message =
        new Message(
            messageRecord.getName(),
            messageRecord.getCorrelationKey(),
            messageRecord.getPayload(),
            messageRecord.getMessageId(),
            messageRecord.getTimeToLive());

    if (messageRecord.hasMessageId() && messageStateController.exist(message)) {
      final String rejectionReason =
          String.format(
              "message with id '%s' is already published",
              bufferAsString(messageRecord.getMessageId()));

      streamWriter.writeRejection(record, RejectionType.BAD_VALUE, rejectionReason);
      responseWriter.writeRejectionOnCommand(record, RejectionType.BAD_VALUE, rejectionReason);

    } else {
      final TypedBatchWriter batchWriter = streamWriter.newBatch();
      final long key = batchWriter.addNewEvent(MessageIntent.PUBLISHED, record.getValue());
      responseWriter.writeEventOnCommand(key, MessageIntent.PUBLISHED, record);
      message.setKey(key);

      matchingSubscriptions =
          messageStateController.findSubscriptions(message.getName(), message.getCorrelationKey());

      for (final MessageSubscription sub : matchingSubscriptions) {
        sub.setMessagePayload(messageRecord.getPayload());
      }

      sideEffect.accept(this::correlateMessage);

      if (messageRecord.getTimeToLive() > 0L) {
        messageStateController.put(message);
      } else {
        // don't add the message to the store to avoid that it can be correlated afterwards
        batchWriter.addFollowUpEvent(key, MessageIntent.DELETED, messageRecord);
      }
    }
  }

  private boolean correlateMessage() {
    for (final MessageSubscription sub : matchingSubscriptions) {
      final boolean success =
          commandSender.correlateWorkflowInstanceSubscription(
              sub.getWorkflowInstancePartitionId(),
              sub.getWorkflowInstanceKey(),
              sub.getActivityInstanceKey(),
              messageRecord.getName(),
              messageRecord.getPayload());

      if (!success) {
        // try again later
        return false;
      }

      sub.setCommandSentTime(ActorClock.currentTimeMillis());
      messageStateController.put(sub);
    }

    return responseWriter.flush();
  }
}
