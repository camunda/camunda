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

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.message.data.MessageRecord;
import io.zeebe.broker.subscription.message.state.Message;
import io.zeebe.broker.subscription.message.state.MessageStateController;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.util.sched.clock.ActorClock;

public class DeleteMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private final MessageStateController messageStateController;

  public DeleteMessageProcessor(MessageStateController messageStateController) {
    this.messageStateController = messageStateController;
  }

  @Override
  public void processRecord(
      TypedRecord<MessageRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {

    final MessageRecord messageRecord = record.getValue();

    final Message message =
        messageStateController.findMessage(
            messageRecord.getName(), messageRecord.getCorrelationKey());

    if (message.getDeadline() < ActorClock.currentTimeMillis()) {
      streamWriter.writeFollowUpEvent(record.getKey(), MessageIntent.DELETED, messageRecord);
      messageStateController.remove(message);
    } else {
      streamWriter.writeRejection(
          record, RejectionType.NOT_APPLICABLE, "Message was updated concurrent.");
    }
  }
}
