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

import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.transport.ServerOutput;
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

  public TypedResponseWriterImpl(ServerOutput output, int partitionId) {
    this.writer = new CommandResponseWriter(output);
    this.partitionId = partitionId;
  }

  @Override
  public void writeRejection(
      TypedRecord<?> record, RejectionType rejectionType, String rejectionReason) {
    final byte[] bytes = rejectionReason.getBytes(StandardCharsets.UTF_8);
    stringWrapper.wrap(bytes);
    stage(
        RecordType.COMMAND_REJECTION,
        record.getMetadata().getIntent(),
        rejectionType,
        stringWrapper,
        record);
  }

  @Override
  public void writeRejection(TypedRecord<?> record, RejectionType type, DirectBuffer reason) {
    stage(RecordType.COMMAND_REJECTION, record.getMetadata().getIntent(), type, reason, record);
  }

  @Override
  public void writeRecord(Intent intent, TypedRecord<?> record) {
    stringWrapper.wrap(0, 0);
    stage(RecordType.EVENT, intent, RejectionType.NULL_VAL, stringWrapper, record);
  }

  private void stage(
      RecordType type,
      Intent intent,
      RejectionType rejectionType,
      DirectBuffer rejectionReason,
      TypedRecord<?> record) {
    final RecordMetadata metadata = record.getMetadata();

    final boolean respondsOnCommand = metadata.getRecordType() == RecordType.COMMAND;

    writer
        .partitionId(partitionId)
        .position(
            respondsOnCommand
                ? 0
                : record.getPosition()) // TODO: this depends on the value of written event =>
        // https://github.com/zeebe-io/zeebe/issues/374
        .sourcePosition(respondsOnCommand ? record.getPosition() : record.getSourcePosition())
        .key(record.getKey())
        .timestamp(record.getTimestamp())
        .intent(intent)
        .recordType(type)
        .valueType(metadata.getValueType())
        .rejectionType(rejectionType)
        .rejectionReason(rejectionReason)
        .valueWriter(record.getValue());

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
