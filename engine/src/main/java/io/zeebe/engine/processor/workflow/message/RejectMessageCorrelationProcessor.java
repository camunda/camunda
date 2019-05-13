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
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;

public class RejectMessageCorrelationProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;
  private MessageSubscription subscription;

  public RejectMessageCorrelationProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final MessageSubscriptionRecord subscriptionRecord = record.getValue();
    final long messageKey = subscriptionRecord.getMessageKey();
    final long workflowInstanceKey = subscriptionRecord.getWorkflowInstanceKey();

    if (!messageState.existMessageCorrelation(messageKey, workflowInstanceKey)) {
      streamWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(
              "Expected message %d to be correlated in workflow instance %d but no correlation was found",
              messageKey, workflowInstanceKey));
      return;
    }
    messageState.removeMessageCorrelation(messageKey, workflowInstanceKey);

    findSubscriptionToCorrelate(sideEffect, subscriptionRecord, messageKey, workflowInstanceKey);

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageSubscriptionIntent.REJECTED, record.getValue());
  }

  private void findSubscriptionToCorrelate(
      final Consumer<SideEffectProducer> sideEffect,
      final MessageSubscriptionRecord subscriptionRecord,
      final long messageKey,
      final long workflowInstanceKey) {

    // the message TTL may expire after the previous correlation attempt
    final Message message = messageState.getMessage(messageKey);
    if (message == null) {
      return;
    }

    subscriptionState.visitSubscriptions(
        subscriptionRecord.getMessageName(),
        subscriptionRecord.getCorrelationKey(),
        subscription -> {
          if (subscription.getWorkflowInstanceKey() == workflowInstanceKey
              && !subscription.isCorrelating()) {
            subscription.setMessageKey(messageKey);
            subscription.setMessageVariables(message.getVariables());

            correlateMessage(subscription, sideEffect);
            return false;
          }
          return true;
        });
  }

  private void correlateMessage(
      final MessageSubscription subscription, final Consumer<SideEffectProducer> sideEffect) {
    subscriptionState.updateToCorrelatingState(
        subscription,
        subscription.getMessageVariables(),
        ActorClock.currentTimeMillis(),
        subscription.getMessageKey());
    messageState.putMessageCorrelation(
        subscription.getMessageKey(), subscription.getWorkflowInstanceKey());
    this.subscription = subscription;

    sideEffect.accept(this::sendCorrelateCommand);
  }

  private boolean sendCorrelateCommand() {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getMessageName(),
        subscription.getMessageKey(),
        subscription.getMessageVariables());
  }
}
