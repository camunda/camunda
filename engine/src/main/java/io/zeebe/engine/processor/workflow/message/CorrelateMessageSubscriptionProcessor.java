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
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;

public class CorrelateMessageSubscriptionProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {
  public static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to correlate subscription for element with key '%d' and message name '%s', "
          + "but no such message subscription exists";

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
          record,
          RejectionType.NOT_FOUND,
          String.format(
              NO_SUBSCRIPTION_FOUND_MESSAGE,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageName())));
    }
  }
}
