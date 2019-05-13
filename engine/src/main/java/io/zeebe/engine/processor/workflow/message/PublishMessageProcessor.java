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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;

public class PublishMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private static final String ALREADY_PUBLISHED_MESSAGE =
      "Expected to publish a new message with id '%s', but a message with that id was already published";
  public static final String ERROR_START_EVENT_NOT_TRIGGERED_MESSAGE =
      "Expected to trigger event for workflow with key '%d', but could not (either does not exist or is not accepting)";

  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final SubscriptionCommandSender commandSender;
  private final KeyGenerator keyGenerator;
  private final EventScopeInstanceState scopeEventInstanceState;

  private TypedResponseWriter responseWriter;
  private MessageRecord messageRecord;
  private long messageKey;

  private final LongArrayList correlatedWorkflowInstances = new LongArrayList();
  private final LongArrayList correlatedElementInstances = new LongArrayList();

  public PublishMessageProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final EventScopeInstanceState scopeEventInstanceState,
      final SubscriptionCommandSender commandSender,
      final KeyGenerator keyGenerator) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.scopeEventInstanceState = scopeEventInstanceState;
    this.commandSender = commandSender;
    this.keyGenerator = keyGenerator;
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
          String.format(ALREADY_PUBLISHED_MESSAGE, bufferAsString(messageRecord.getMessageId()));

      streamWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, rejectionReason);
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.ALREADY_EXISTS, rejectionReason);
    } else {
      handleNewMessage(command, responseWriter, streamWriter, sideEffect);
    }
  }

  private void handleNewMessage(
      final TypedRecord<MessageRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    messageKey = keyGenerator.nextKey();

    streamWriter.appendNewEvent(messageKey, MessageIntent.PUBLISHED, command.getValue());
    responseWriter.writeEventOnCommand(
        messageKey, MessageIntent.PUBLISHED, command.getValue(), command);

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
                subscription,
                messageRecord.getVariables(),
                ActorClock.currentTimeMillis(),
                messageKey);

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
              messageKey,
              messageRecord.getName(),
              messageRecord.getCorrelationKey(),
              messageRecord.getVariables(),
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
      streamWriter.appendFollowUpEvent(messageKey, MessageIntent.DELETED, messageRecord);
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
              messageKey,
              messageRecord.getVariables());

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
        subscription -> visitStartEventSubscription(command, streamWriter, subscription));
  }

  private void visitStartEventSubscription(
      TypedRecord<MessageRecord> command,
      TypedStreamWriter streamWriter,
      MessageStartEventSubscriptionRecord subscription) {
    final DirectBuffer startEventId = subscription.getStartEventId();
    final long workflowKey = subscription.getWorkflowKey();
    final WorkflowInstanceRecord record =
        new WorkflowInstanceRecord()
            .setWorkflowKey(workflowKey)
            .setElementId(startEventId)
            .setBpmnElementType(BpmnElementType.START_EVENT);

    final long eventKey = keyGenerator.nextKey();
    final boolean wasTriggered =
        scopeEventInstanceState.triggerEvent(
            workflowKey, eventKey, startEventId, command.getValue().getVariables());

    if (wasTriggered) {
      streamWriter.appendNewEvent(eventKey, WorkflowInstanceIntent.EVENT_OCCURRED, record);
    } else {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
          String.format(ERROR_START_EVENT_NOT_TRIGGERED_MESSAGE, workflowKey));
    }
  }
}
