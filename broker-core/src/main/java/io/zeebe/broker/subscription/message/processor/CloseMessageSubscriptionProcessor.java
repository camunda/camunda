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
import io.zeebe.broker.subscription.message.state.MessageSubscriptionState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import java.util.function.Consumer;

public class CloseMessageSubscriptionProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;

  private MessageSubscriptionRecord subscriptionRecord;

  public CloseMessageSubscriptionProcessor(
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender) {
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    subscriptionRecord = record.getValue();

    final boolean removed =
        subscriptionState.remove(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageName());
    if (removed) {
      streamWriter.appendFollowUpEvent(
          record.getKey(), MessageSubscriptionIntent.CLOSED, subscriptionRecord);

    } else {
      streamWriter.appendRejection(
          record, RejectionType.NOT_APPLICABLE, "subscription is already closed");
    }

    sideEffect.accept(this::sendAcknowledgeCommand);
  }

  private boolean sendAcknowledgeCommand() {
    return commandSender.closeWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageName());
  }
}
