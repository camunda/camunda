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

import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.keyNullValue;
import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.partitionIdNullValue;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecuteCommandRequest implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteCommandRequestEncoder bodyEncoder = new ExecuteCommandRequestEncoder();
  private final ExecuteCommandRequestDecoder bodyDecoder = new ExecuteCommandRequestDecoder();

  private int partitionId;
  private long key;
  private ValueType valueType;
  private Intent intent;
  private final DirectBuffer value = new UnsafeBuffer(0, 0);

  public ExecuteCommandRequest() {
    reset();
  }

  public ExecuteCommandRequest reset() {
    partitionId = partitionIdNullValue();
    key = keyNullValue();
    valueType = ValueType.NULL_VAL;
    intent = Intent.UNKNOWN;
    value.wrap(0, 0);

    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ExecuteCommandRequest setPartitionId(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getKey() {
    return key;
  }

  public ExecuteCommandRequest setKey(long key) {
    this.key = key;
    this.partitionId = Protocol.decodePartitionId(key);

    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public ExecuteCommandRequest setValueType(ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public Intent getIntent() {
    return intent;
  }

  public ExecuteCommandRequest setIntent(Intent intent) {
    this.intent = intent;
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  public ExecuteCommandRequest setValue(DirectBuffer buffer, int offset, int length) {
    this.value.wrap(buffer, offset, length);
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
    valueType = bodyDecoder.valueType();
    intent = Intent.fromProtocolValue(valueType, bodyDecoder.intent());

    offset += bodyDecoder.sbeBlockLength();

    final int valueLength = bodyDecoder.valueLength();
    offset += ExecuteCommandRequestDecoder.valueHeaderLength();

    value.wrap(buffer, offset, valueLength);
    offset += valueLength;

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
        + ExecuteCommandRequestEncoder.BLOCK_LENGTH
        + ExecuteCommandRequestEncoder.valueHeaderLength()
        + value.capacity();
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
        .valueType(valueType)
        .intent(intent.value())
        .putValue(value, 0, value.capacity());
  }
}
