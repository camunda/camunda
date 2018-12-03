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
import io.zeebe.broker.subscription.message.state.MessageState;
import io.zeebe.broker.subscription.message.state.MessageSubscription;
import io.zeebe.broker.subscription.message.state.MessageSubscriptionState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import java.util.function.Consumer;

public class OpenMessageSubscriptionProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final MessageCorrelator messageCorrelator;
  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;

  private MessageSubscriptionRecord subscriptionRecord;

  public OpenMessageSubscriptionProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender) {
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
    this.messageCorrelator = new MessageCorrelator(messageState, subscriptionState, commandSender);
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    subscriptionRecord = record.getValue();

    if (subscriptionState.existSubscriptionForElementInstance(
        subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageName())) {
      sideEffect.accept(this::sendAcknowledgeCommand);

      streamWriter.appendRejection(
          record, RejectionType.NOT_APPLICABLE, "subscription is already open");
      return;
    }

    handleNewSubscription(record, streamWriter, sideEffect);
  }

  private void handleNewSubscription(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    final MessageSubscription subscription =
        new MessageSubscription(
            subscriptionRecord.getWorkflowInstanceKey(),
            subscriptionRecord.getElementInstanceKey(),
            subscriptionRecord.getMessageName(),
            subscriptionRecord.getCorrelationKey(),
            subscriptionRecord.shouldCloseOnCorrelate());

    sideEffect.accept(this::sendAcknowledgeCommand);

    subscriptionState.put(subscription);
    messageCorrelator.correlateNextMessage(subscription, subscriptionRecord, sideEffect);

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageSubscriptionIntent.OPENED, subscriptionRecord);
  }

  private boolean sendAcknowledgeCommand() {
    return commandSender.openWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageName(),
        subscriptionRecord.shouldCloseOnCorrelate());
  }
}
