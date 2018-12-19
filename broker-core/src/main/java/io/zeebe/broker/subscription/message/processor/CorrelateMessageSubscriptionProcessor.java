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

public class CorrelateMessageSubscriptionProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final MessageSubscriptionState subscriptionState;
  private final MessageCorrelator messageCorrelator;

  public CorrelateMessageSubscriptionProcessor(
      MessageState messageState,
      MessageSubscriptionState subscriptionState,
      SubscriptionCommandSender commandSender) {
    this.subscriptionState = subscriptionState;
    this.messageCorrelator = new MessageCorrelator(messageState, subscriptionState, commandSender);
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final MessageSubscriptionRecord subscriptionRecord = record.getValue();
    final MessageSubscription subscription =
        subscriptionState.get(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageName());

    if (subscription != null) {
      if (subscription.shouldCloseOnCorrelate()) {
        subscriptionState.remove(subscription);
      } else {
        subscriptionState.resetSentTime(subscription);
        messageCorrelator.correlateNextMessage(subscription, subscriptionRecord, sideEffect);
      }

      streamWriter.appendFollowUpEvent(
          record.getKey(), MessageSubscriptionIntent.CORRELATED, subscriptionRecord);
    } else {
      streamWriter.appendRejection(
          record, RejectionType.NOT_APPLICABLE, "subscription does not exist");
    }
  }
}
