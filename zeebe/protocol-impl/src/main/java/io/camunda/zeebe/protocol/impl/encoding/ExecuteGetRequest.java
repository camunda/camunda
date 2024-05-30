/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.record.ExecuteGetRequestDecoder;
import io.camunda.zeebe.protocol.record.ExecuteGetRequestEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class ExecuteGetRequest implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteGetRequestEncoder bodyEncoder = new ExecuteGetRequestEncoder();
  private final ExecuteGetRequestDecoder bodyDecoder = new ExecuteGetRequestDecoder();

  private int partitionId;
  private long key;
  private ValueType valueType;

  public ExecuteGetRequest() {
    reset();
  }

  public ExecuteGetRequest reset() {
    partitionId = ExecuteGetRequestEncoder.partitionIdNullValue();
    key = ExecuteGetRequestEncoder.keyNullValue();
    valueType = ValueType.NULL_VAL;

    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ExecuteGetRequest setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getKey() {
    return key;
  }

  public ExecuteGetRequest setKey(final long key) {
    this.key = key;
    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public ExecuteGetRequest setValueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .partitionId(partitionId)
        .key(key)
        .valueType(valueType);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    partitionId = bodyDecoder.partitionId();
    key = bodyDecoder.key();
    valueType = bodyDecoder.valueType();
  }
}
