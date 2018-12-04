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
package io.zeebe.protocol.impl.record;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.RecordMetadataDecoder;
import io.zeebe.protocol.clientapi.RecordMetadataEncoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RecordMetadata implements BufferWriter, BufferReader {
  public static final int BLOCK_LENGTH =
      MessageHeaderEncoder.ENCODED_LENGTH + RecordMetadataEncoder.BLOCK_LENGTH;

  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  protected RecordMetadataEncoder encoder = new RecordMetadataEncoder();
  protected RecordMetadataDecoder decoder = new RecordMetadataDecoder();

  private RecordType recordType = RecordType.NULL_VAL;
  private short intentValue = Intent.NULL_VAL;
  private Intent intent = null;
  protected int requestStreamId;
  protected long requestId;
  protected int protocolVersion =
      Protocol.PROTOCOL_VERSION; // always the current version by default
  protected ValueType valueType = ValueType.NULL_VAL;
  private RejectionType rejectionType;
  private final UnsafeBuffer rejectionReason = new UnsafeBuffer(0, 0);

  public RecordMetadata() {
    reset();
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    reset();

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    decoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    recordType = decoder.recordType();
    requestStreamId = decoder.requestStreamId();
    requestId = decoder.requestId();
    protocolVersion = decoder.protocolVersion();
    valueType = decoder.valueType();
    intent = Intent.fromProtocolValue(valueType, decoder.intent());
    rejectionType = decoder.rejectionType();

    final int rejectionReasonLength = decoder.rejectionReasonLength();

    offset += headerDecoder.blockLength();
    offset += RecordMetadataDecoder.rejectionReasonHeaderLength();

    rejectionReason.wrap(buffer, offset, rejectionReasonLength);
  }

  @Override
  public int getLength() {
    return BLOCK_LENGTH
        + RecordMetadataEncoder.rejectionReasonHeaderLength()
        + rejectionReason.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    headerEncoder.wrap(buffer, offset);

    headerEncoder
        .blockLength(encoder.sbeBlockLength())
        .templateId(encoder.sbeTemplateId())
        .schemaId(encoder.sbeSchemaId())
        .version(encoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    encoder.wrap(buffer, offset);

    encoder
        .recordType(recordType)
        .requestStreamId(requestStreamId)
        .requestId(requestId)
        .protocolVersion(protocolVersion)
        .valueType(valueType)
        .intent(intentValue)
        .rejectionType(rejectionType);

    offset += RecordMetadataEncoder.BLOCK_LENGTH;

    if (rejectionReason.capacity() > 0) {
      encoder.putRejectionReason(rejectionReason, 0, rejectionReason.capacity());
    } else {
      buffer.putShort(offset, (short) 0);
    }
  }

  public long getRequestId() {
    return requestId;
  }

  public RecordMetadata requestId(long requestId) {
    this.requestId = requestId;
    return this;
  }

  public int getRequestStreamId() {
    return requestStreamId;
  }

  public RecordMetadata requestStreamId(int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  public RecordMetadata protocolVersion(int protocolVersion) {
    this.protocolVersion = protocolVersion;
    return this;
  }

  public int getProtocolVersion() {
    return protocolVersion;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public RecordMetadata valueType(ValueType eventType) {
    this.valueType = eventType;
    return this;
  }

  public RecordMetadata intent(Intent intent) {
    this.intent = intent;
    this.intentValue = intent.value();
    return this;
  }

  public Intent getIntent() {
    return intent;
  }

  public RecordMetadata recordType(RecordType recordType) {
    this.recordType = recordType;
    return this;
  }

  public RecordType getRecordType() {
    return recordType;
  }

  public RecordMetadata rejectionType(RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public RecordMetadata rejectionReason(String rejectionReason) {
    final byte[] bytes = rejectionReason.getBytes(StandardCharsets.UTF_8);
    this.rejectionReason.wrap(bytes);
    return this;
  }

  public RecordMetadata rejectionReason(DirectBuffer buffer) {
    this.rejectionReason.wrap(buffer);
    return this;
  }

  public DirectBuffer getRejectionReason() {
    return rejectionReason;
  }

  public RecordMetadata reset() {
    recordType = RecordType.NULL_VAL;
    requestId = RecordMetadataEncoder.requestIdNullValue();
    requestStreamId = RecordMetadataEncoder.requestStreamIdNullValue();
    protocolVersion = Protocol.PROTOCOL_VERSION;
    valueType = ValueType.NULL_VAL;
    intentValue = Intent.NULL_VAL;
    intent = null;
    rejectionType = RejectionType.NULL_VAL;
    rejectionReason.wrap(0, 0);
    return this;
  }

  public boolean hasRequestMetadata() {
    return requestId != RecordMetadataEncoder.requestIdNullValue()
        && requestStreamId != RecordMetadataEncoder.requestStreamIdNullValue();
  }

  public void copyRequestMetadata(RecordMetadata target) {
    target.requestId(requestId).requestStreamId(requestStreamId);
  }

  @Override
  public String toString() {
    return "RecordMetadata{"
        + "recordType="
        + recordType
        + ", intentValue="
        + intentValue
        + ", intent="
        + intent
        + ", requestStreamId="
        + requestStreamId
        + ", requestId="
        + requestId
        + ", valueType="
        + valueType
        + '}';
  }
}
