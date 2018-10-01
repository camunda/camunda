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
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.Intent;

public class CommandProcessorImpl<T extends UnpackedObject>
    implements TypedRecordProcessor<T>, CommandControl {

  private final CommandProcessor<T> wrappedProcessor;

  private KeyGenerator keyGenerator;

  private boolean isAccepted;
  private long entityKey;

  private Intent newState;

  private RejectionType rejectionType;
  private String rejectionReason;

  public CommandProcessorImpl(CommandProcessor<T> commandProcessor) {
    this.wrappedProcessor = commandProcessor;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    this.keyGenerator = streamProcessor.getKeyGenerator();
  }

  @Override
  public void processRecord(
      TypedRecord<T> command, TypedResponseWriter responseWriter, TypedStreamWriter streamWriter) {

    entityKey = command.getKey();

    wrappedProcessor.onCommand(command, this);

    final boolean respond = command.getMetadata().hasRequestMetadata();

    if (isAccepted) {
      streamWriter.writeFollowUpEvent(entityKey, newState, command.getValue());
      if (respond) {
        responseWriter.writeEventOnCommand(entityKey, newState, command.getValue(), command);
      }
    } else {
      streamWriter.writeRejection(command, rejectionType, rejectionReason);
      if (respond) {
        responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
      }
    }
  }

  @Override
  public long accept(Intent newState) {
    if (entityKey < 0) {
      entityKey = keyGenerator.nextKey();
    }

    isAccepted = true;
    this.newState = newState;
    return entityKey;
  }

  @Override
  public void reject(RejectionType type, String reason) {
    isAccepted = false;
    this.rejectionType = type;
    this.rejectionReason = reason;
  }
}
