/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.protocol.impl.encoding;

import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.keyNullValue;
import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.partitionIdNullValue;

import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecuteCommandResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteCommandResponseEncoder bodyEncoder = new ExecuteCommandResponseEncoder();
  private final ExecuteCommandResponseDecoder bodyDecoder = new ExecuteCommandResponseDecoder();

  private int partitionId;
  private long key;
  private RecordType recordType;
  private ValueType valueType;
  private Intent intent;
  private RejectionType rejectionType;
  private final DirectBuffer value = new UnsafeBuffer(0, 0);
  private final DirectBuffer rejectionReason = new UnsafeBuffer(0, 0);

  public ExecuteCommandResponse() {
    reset();
  }

  public ExecuteCommandResponse reset() {
    partitionId = partitionIdNullValue();
    key = keyNullValue();
    recordType = RecordType.NULL_VAL;
    valueType = ValueType.NULL_VAL;
    intent = Intent.UNKNOWN;
    rejectionType = RejectionType.NULL_VAL;
    value.wrap(0, 0);
    rejectionReason.wrap(0, 0);

    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ExecuteCommandResponse setPartitionId(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getKey() {
    return key;
  }

  public ExecuteCommandResponse setKey(long key) {
    this.key = key;
    return this;
  }

  public RecordType getRecordType() {
    return recordType;
  }

  public ExecuteCommandResponse setRecordType(RecordType recordType) {
    this.recordType = recordType;
    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public ExecuteCommandResponse setValueType(ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public Intent getIntent() {
    return intent;
  }

  public ExecuteCommandResponse setIntent(Intent intent) {
    this.intent = intent;
    return this;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public ExecuteCommandResponse setRejectionType(RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  public ExecuteCommandResponse setValue(DirectBuffer buffer, int offset, int length) {
    this.value.wrap(buffer, offset, length);
    return this;
  }

  public DirectBuffer getRejectionReason() {
    return rejectionReason;
  }

  public ExecuteCommandResponse setRejectionReason(DirectBuffer buffer, int offset, int length) {
    this.rejectionReason.wrap(buffer, offset, length);
    return this;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    reset();

    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    partitionId = bodyDecoder.partitionId();
    key = bodyDecoder.key();
    recordType = bodyDecoder.recordType();
    valueType = bodyDecoder.valueType();
    intent = Intent.fromProtocolValue(valueType, bodyDecoder.intent());
    rejectionType = bodyDecoder.rejectionType();

    offset += bodyDecoder.sbeBlockLength();

    final int valueLength = bodyDecoder.valueLength();
    offset += ExecuteCommandResponseDecoder.valueHeaderLength();

    if (valueLength > 0) {
      value.wrap(buffer, offset, valueLength);
    }
    offset += valueLength;
    bodyDecoder.limit(offset);

    final int rejectionReasonLength = bodyDecoder.rejectionReasonLength();
    offset += ExecuteCommandResponseDecoder.rejectionReasonHeaderLength();

    if (rejectionReasonLength > 0) {
      rejectionReason.wrap(buffer, offset, rejectionReasonLength);
    }
    offset += rejectionReasonLength;
    bodyDecoder.limit(offset);

    assert bodyDecoder.limit() == frameEnd
        : "Decoder read only to position "
            + bodyDecoder.limit()
            + " but expected "
            + frameEnd
            + " as final position";
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteCommandResponseEncoder.BLOCK_LENGTH
        + ExecuteCommandResponseEncoder.valueHeaderLength()
        + value.capacity()
        + ExecuteCommandResponseEncoder.rejectionReasonHeaderLength()
        + rejectionReason.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    bodyEncoder
        .wrap(buffer, offset)
        .partitionId(partitionId)
        .key(key)
        .recordType(recordType)
        .valueType(valueType)
        .intent(intent.value())
        .rejectionType(rejectionType)
        .putValue(value, 0, value.capacity())
        .putRejectionReason(rejectionReason, 0, rejectionReason.capacity());
  }
}
