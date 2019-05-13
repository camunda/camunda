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

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.intent.MessageIntent;

public class DeleteMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private final MessageState messageState;

  public DeleteMessageProcessor(final MessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    streamWriter.appendFollowUpEvent(record.getKey(), MessageIntent.DELETED, record.getValue());

    messageState.remove(record.getKey());
  }
}
