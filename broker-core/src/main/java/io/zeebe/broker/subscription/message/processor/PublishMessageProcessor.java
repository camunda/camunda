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
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.Message;
import io.zeebe.broker.subscription.message.state.MessageStartEventSubscriptionState;
import io.zeebe.broker.subscription.message.state.MessageState;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;

public class PublishMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final SubscriptionCommandSender commandSender;

  private TypedResponseWriter responseWriter;
  private MessageRecord messageRecord;

  private final LongArrayList correlatedWorkflowInstances = new LongArrayList();
  private final LongArrayList correlatedElementInstances = new LongArrayList();

  public PublishMessageProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final SubscriptionCommandSender commandSender) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.commandSender = commandSender;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    this.responseWriter = responseWriter;
    messageRecord = command.getValue();

    if (messageRecord.hasMessageId()
        && messageState.exist(
            messageRecord.getName(),
            messageRecord.getCorrelationKey(),
            messageRecord.getMessageId())) {
      final String rejectionReason =
          String.format(
              "message with id '%s' is already published",
              bufferAsString(messageRecord.getMessageId()));

      streamWriter.appendRejection(command, RejectionType.BAD_VALUE, rejectionReason);
      responseWriter.writeRejectionOnCommand(command, RejectionType.BAD_VALUE, rejectionReason);

    } else {
      handleNewMessage(command, responseWriter, streamWriter, sideEffect);
    }
  }

  private void handleNewMessage(
      final TypedRecord<MessageRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final long key = streamWriter.appendNewEvent(MessageIntent.PUBLISHED, command.getValue());
    responseWriter.writeEventOnCommand(key, MessageIntent.PUBLISHED, command.getValue(), command);

    correlatedWorkflowInstances.clear();
    correlatedElementInstances.clear();

    subscriptionState.visitSubscriptions(
        messageRecord.getName(),
        messageRecord.getCorrelationKey(),
        subscription -> {
          final long workflowInstanceKey = subscription.getWorkflowInstanceKey();
          final long elementInstanceKey = subscription.getElementInstanceKey();

          // correlate the message only once per workflow instance
          if (!subscription.isCorrelating()
              && !correlatedWorkflowInstances.containsLong(workflowInstanceKey)) {

            subscriptionState.updateToCorrelatingState(
                subscription, messageRecord.getPayload(), ActorClock.currentTimeMillis());

            correlatedWorkflowInstances.addLong(workflowInstanceKey);
            correlatedElementInstances.addLong(elementInstanceKey);
          }

          return true;
        });

    sideEffect.accept(this::correlateMessage);

    correlateMessageStartEvents(command, streamWriter);

    if (messageRecord.getTimeToLive() > 0L) {
      final Message message =
          new Message(
              key,
              messageRecord.getName(),
              messageRecord.getCorrelationKey(),
              messageRecord.getPayload(),
              messageRecord.getMessageId(),
              messageRecord.getTimeToLive(),
              messageRecord.getTimeToLive() + ActorClock.currentTimeMillis());
      messageState.put(message);

      correlatedWorkflowInstances.forEachOrderedLong(
          workflowInstanceKey -> {
            messageState.putMessageCorrelation(message.getKey(), workflowInstanceKey);
          });

    } else {
      // don't add the message to the store to avoid that it can be correlated afterwards
      streamWriter.appendFollowUpEvent(key, MessageIntent.DELETED, messageRecord);
    }
  }

  private boolean correlateMessage() {
    for (int i = 0; i < correlatedWorkflowInstances.size(); i++) {
      final long workflowInstanceKey = correlatedWorkflowInstances.getLong(i);
      final long elementInstanceKey = correlatedElementInstances.getLong(i);

      final boolean success =
          commandSender.correlateWorkflowInstanceSubscription(
              workflowInstanceKey,
              elementInstanceKey,
              messageRecord.getName(),
              messageRecord.getPayload());

      if (!success) {
        return false;
      }
    }

    return responseWriter.flush();
  }

  private void correlateMessageStartEvents(
      final TypedRecord<MessageRecord> command, final TypedStreamWriter streamWriter) {
    final DirectBuffer messageName = command.getValue().getName();
    startEventSubscriptionState.visitSubscriptionsByMessageName(
        messageName,
        subscription -> {
          final DirectBuffer startEventId = subscription.getStartEventId();
          final long workflowKey = subscription.getWorkflowKey();

          final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
          record
              .setWorkflowKey(workflowKey)
              .setElementId(startEventId)
              .setPayload(command.getValue().getPayload());

          streamWriter.appendNewEvent(WorkflowInstanceIntent.EVENT_OCCURRED, record);
        });
  }
}
