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
package io.zeebe.engine.processor;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TypedResponseWriterImpl implements TypedResponseWriter, SideEffectProducer {

  protected CommandResponseWriter writer;
  private long requestId;
  private int requestStreamId;

  private boolean isResponseStaged;
  protected int partitionId;

  private final UnsafeBuffer stringWrapper = new UnsafeBuffer(0, 0);

  public TypedResponseWriterImpl(CommandResponseWriter writer, int partitionId) {
    this.writer = writer;
    this.partitionId = partitionId;
  }

  @Override
  public void writeRejection(TypedRecord<?> rejection) {

    final RecordMetadata metadata = rejection.getMetadata();

    stage(
        RecordType.COMMAND_REJECTION,
        metadata.getIntent(),
        rejection.getKey(),
        rejection.getMetadata().getRejectionType(),
        rejection.getMetadata().getRejectionReason(),
        rejection.getMetadata(),
        rejection.getValue());
  }

  @Override
  public void writeRejectionOnCommand(TypedRecord<?> command, RejectionType type, String reason) {
    final byte[] bytes = reason.getBytes(StandardCharsets.UTF_8);
    stringWrapper.wrap(bytes);

    stage(
        RecordType.COMMAND_REJECTION,
        command.getMetadata().getIntent(),
        command.getKey(),
        type,
        stringWrapper,
        command.getMetadata(),
        command.getValue());
  }

  @Override
  public void writeRejectionOnCommand(
      TypedRecord<?> command, RejectionType type, DirectBuffer reason) {

    stage(
        RecordType.COMMAND_REJECTION,
        command.getMetadata().getIntent(),
        command.getKey(),
        type,
        reason,
        command.getMetadata(),
        command.getValue());
  }

  @Override
  public void writeEvent(TypedRecord<?> event) {
    stringWrapper.wrap(0, 0);

    stage(
        RecordType.EVENT,
        event.getMetadata().getIntent(),
        event.getKey(),
        RejectionType.NULL_VAL,
        stringWrapper,
        event.getMetadata(),
        event.getValue());
  }

  @Override
  public void writeEventOnCommand(
      long eventKey, Intent eventState, UnpackedObject eventValue, TypedRecord<?> command) {
    stringWrapper.wrap(0, 0);

    stage(
        RecordType.EVENT,
        eventState,
        eventKey,
        RejectionType.NULL_VAL,
        stringWrapper,
        command.getMetadata(),
        eventValue);
  }

  private void stage(
      RecordType type,
      Intent intent,
      long key,
      RejectionType rejectionType,
      DirectBuffer rejectionReason,
      RecordMetadata metadata,
      UnpackedObject value) {
    writer
        .partitionId(partitionId)
        .key(key)
        .intent(intent)
        .recordType(type)
        .valueType(metadata.getValueType())
        .rejectionType(rejectionType)
        .rejectionReason(rejectionReason)
        .valueWriter(value);

    this.requestId = metadata.getRequestId();
    this.requestStreamId = metadata.getRequestStreamId();
    isResponseStaged = true;
  }

  public void reset() {
    isResponseStaged = false;
  }

  public boolean flush() {
    if (isResponseStaged) {
      return writer.tryWriteResponse(requestStreamId, requestId);
    } else {
      return true;
    }
  }
}
