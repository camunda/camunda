/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder.keyNullValue;
import static io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder.operationReferenceNullValue;
import static io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder.partitionIdNullValue;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExecuteCommandRequest implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteCommandRequestEncoder bodyEncoder = new ExecuteCommandRequestEncoder();
  private final ExecuteCommandRequestDecoder bodyDecoder = new ExecuteCommandRequestDecoder();
  private final DirectBuffer value = new UnsafeBuffer(0, 0);
  private int partitionId;
  private long key;
  private long operationReference;
  private ValueType valueType;
  private Intent intent;
  private final AuthInfo authorization = new AuthInfo();

  public ExecuteCommandRequest() {
    reset();
  }

  public ExecuteCommandRequest reset() {
    partitionId = partitionIdNullValue();
    key = keyNullValue();
    operationReference = operationReferenceNullValue();
    valueType = ValueType.NULL_VAL;
    intent = Intent.UNKNOWN;
    value.wrap(0, 0);
    authorization.reset();

    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ExecuteCommandRequest setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getKey() {
    return key;
  }

  /**
   * Sets the key for this command request.
   *
   * <p>Note: This method also extracts and sets the partitionId from the key if valid. The
   * partitionId should only be updated when it falls within the valid range, otherwise it remains
   * unchanged keeping the default value from {@link
   * ExecuteCommandRequestEncoder#partitionIdNullValue()} and will be handled by {@link
   * io.camunda.zeebe.broker.client.impl.BrokerRequestManager}.
   *
   * @param key the key to set
   * @return this request instance for method chaining
   */
  public ExecuteCommandRequest setKey(final long key) {
    this.key = key;
    final int decodedPartitionId = Protocol.decodePartitionId(key);
    if (decodedPartitionId >= Protocol.START_PARTITION_ID
        && decodedPartitionId <= Protocol.MAXIMUM_PARTITIONS) {
      partitionId = decodedPartitionId;
    }

    return this;
  }

  public long getOperationReference() {
    return operationReference;
  }

  public ExecuteCommandRequest setOperationReference(final long operationReference) {
    this.operationReference = operationReference;
    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public ExecuteCommandRequest setValueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public Intent getIntent() {
    return intent;
  }

  public ExecuteCommandRequest setIntent(final Intent intent) {
    this.intent = intent;
    return this;
  }

  public DirectBuffer getValue() {
    return value;
  }

  public ExecuteCommandRequest setValue(
      final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
    return this;
  }

  public AuthInfo getAuthorization() {
    return authorization;
  }

  public ExecuteCommandRequest setAuthorization(final AuthInfo authorization) {
    this.authorization.copyFrom(authorization);
    return this;
  }

  public ExecuteCommandRequest setAuthorization(final DirectBuffer buffer) {
    authorization.wrap(buffer);
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    reset();

    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    partitionId = bodyDecoder.partitionId();
    key = bodyDecoder.key();
    operationReference = bodyDecoder.operationReference();
    valueType = bodyDecoder.valueType();
    intent = Intent.fromProtocolValue(valueType, bodyDecoder.intent());

    offset += bodyDecoder.sbeBlockLength();

    final int valueLength = bodyDecoder.valueLength();
    offset += ExecuteCommandRequestDecoder.valueHeaderLength();

    value.wrap(buffer, offset, valueLength);
    offset += valueLength;

    final int authorizationLength = bodyDecoder.authorizationLength();
    offset += ExecuteCommandRequestDecoder.authorizationHeaderLength();

    authorization.wrap(buffer, offset, authorizationLength);
    offset += authorizationLength;

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
        + value.capacity()
        + ExecuteCommandRequestEncoder.authorizationHeaderLength()
        + authorization.getLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, int offset) {
    final int initialOffset = offset;
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
        .operationReference(operationReference)
        .valueType(valueType)
        .intent(intent.value())
        .putValue(value, 0, value.capacity())
        .putAuthorization(authorization.toDirectBuffer(), 0, authorization.getLength());

    return bodyEncoder.limit() - initialOffset;
  }
}
