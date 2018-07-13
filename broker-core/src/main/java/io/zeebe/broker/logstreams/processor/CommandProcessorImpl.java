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
package io.zeebe.broker.logstreams.processor;

import io.zeebe.broker.logstreams.processor.CommandProcessor.CommandControl;
import io.zeebe.broker.logstreams.processor.CommandProcessor.CommandResult;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.Intent;

public class CommandProcessorImpl<T extends UnpackedObject>
    implements TypedRecordProcessor<T>, CommandControl, CommandResult {

  private final CommandProcessor<T> wrappedProcessor;

  private boolean isAccepted;

  private Intent newState;

  private RejectionType rejectionType;
  private String rejectionReason;

  public CommandProcessorImpl(CommandProcessor<T> commandProcessor) {
    this.wrappedProcessor = commandProcessor;
  }

  @Override
  public void processRecord(
      TypedRecord<T> record, TypedResponseWriter responseWriter, TypedStreamWriter streamWriter) {
    wrappedProcessor.onCommand(record, this);

    final boolean respond = record.getMetadata().hasRequestMetadata();

    if (isAccepted) {
      streamWriter.writeFollowUpEvent(record.getKey(), newState, record.getValue());
      if (respond) {
        responseWriter.writeRecord(newState, record);
      }
    } else {
      streamWriter.writeRejection(record, rejectionType, rejectionReason);
      if (respond) {
        responseWriter.writeRejection(record, rejectionType, rejectionReason);
      }
    }
  }

  @Override
  public CommandResult accept(Intent newState) {
    isAccepted = true;
    this.newState = newState;
    return this;
  }

  @Override
  public CommandResult reject(RejectionType type, String reason) {
    isAccepted = false;
    this.rejectionType = type;
    this.rejectionReason = reason;
    return this;
  }
}
