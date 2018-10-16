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

import static io.zeebe.protocol.clientapi.ControlMessageRequestEncoder.partitionIdNullValue;

import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ControlMessageRequestEncoder;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ControlMessageRequest implements BufferReader, BufferWriter {

  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  protected final ControlMessageRequestEncoder bodyEncoder = new ControlMessageRequestEncoder();
  protected final ControlMessageRequestDecoder bodyDecoder = new ControlMessageRequestDecoder();

  protected ControlMessageType messageType;
  protected int partitionId;
  protected final DirectBuffer data = new UnsafeBuffer(0, 0);

  public ControlMessageRequest() {
    reset();
  }

  public ControlMessageRequest reset() {
    messageType = ControlMessageType.NULL_VAL;
    partitionId = partitionIdNullValue();
    data.wrap(0, 0);

    return this;
  }

  public ControlMessageType getMessageType() {
    return messageType;
  }

  public ControlMessageRequest setMessageType(ControlMessageType messageType) {
    this.messageType = messageType;
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ControlMessageRequest setPartitionId(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public ControlMessageRequest setData(DirectBuffer buffer, int offset, int length) {
    this.data.wrap(buffer, offset, length);
    return this;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    reset();

    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    messageType = bodyDecoder.messageType();
    partitionId = bodyDecoder.partitionId();

    offset += bodyDecoder.sbeBlockLength();

    final int dataLength = bodyDecoder.dataLength();
    offset += ControlMessageRequestDecoder.dataHeaderLength();

    data.wrap(buffer, offset, dataLength);
    offset += dataLength;

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
        + ControlMessageRequestEncoder.BLOCK_LENGTH
        + ControlMessageRequestEncoder.dataHeaderLength()
        + data.capacity();
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
        .messageType(messageType)
        .partitionId(partitionId)
        .putData(data, 0, data.capacity());
  }
}
