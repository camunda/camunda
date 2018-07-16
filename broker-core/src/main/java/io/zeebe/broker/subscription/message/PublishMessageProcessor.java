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
package io.zeebe.broker.subscription.message;

import static io.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.message.data.MessageRecord;
import io.zeebe.broker.subscription.message.state.MessageDataStore;
import io.zeebe.broker.subscription.message.state.MessageDataStore.MessageEntry;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.MessageIntent;

public class PublishMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private final MessageDataStore dataStore;

  private MessageEntry entry;

  private boolean isDuplicate;
  private String rejectionReason;

  public PublishMessageProcessor(MessageDataStore dataStore) {
    this.dataStore = dataStore;
  }

  @Override
  public void processRecord(TypedRecord<MessageRecord> record) {
    isDuplicate = false;

    final MessageRecord message = record.getValue();
    entry =
        new MessageEntry(
            bufferAsString(message.getName()),
            bufferAsString(message.getCorrelationKey()),
            bufferAsArray(message.getPayload()),
            message.hasMessageId() ? bufferAsString(message.getMessageId()) : null);

    if (message.hasMessageId() && dataStore.hasMessage(entry)) {
      isDuplicate = true;

      rejectionReason =
          String.format(
              "message with id '%s' is already published", bufferAsString(message.getMessageId()));
    }
  }

  @Override
  public boolean executeSideEffects(
      TypedRecord<MessageRecord> record, TypedResponseWriter responseWriter) {

    if (isDuplicate) {
      return responseWriter.writeRejection(record, RejectionType.BAD_VALUE, rejectionReason);
    }

    return responseWriter.writeRecord(MessageIntent.PUBLISHED, record);
  }

  @Override
  public long writeRecord(TypedRecord<MessageRecord> record, TypedStreamWriter writer) {

    if (isDuplicate) {
      return writer.writeRejection(record, RejectionType.BAD_VALUE, rejectionReason);
    }

    return writer.writeFollowUpEvent(record.getKey(), MessageIntent.PUBLISHED, record.getValue());
  }

  @Override
  public void updateState(TypedRecord<MessageRecord> record) {
    if (!isDuplicate) {
      dataStore.addMessage(entry);
    }
  }
}
