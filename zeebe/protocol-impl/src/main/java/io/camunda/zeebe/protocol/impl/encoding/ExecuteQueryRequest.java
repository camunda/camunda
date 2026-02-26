/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.protocol.record.ExecuteQueryRequestDecoder;
import io.camunda.zeebe.protocol.record.ExecuteQueryRequestEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class ExecuteQueryRequest implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteQueryRequestEncoder bodyEncoder = new ExecuteQueryRequestEncoder();
  private final ExecuteQueryRequestDecoder bodyDecoder = new ExecuteQueryRequestDecoder();

  private int partitionId;
  private long key;
  private ValueType valueType;

  public ExecuteQueryRequest() {
    reset();
  }

  public ExecuteQueryRequest reset() {
    partitionId = ExecuteQueryRequestEncoder.partitionIdNullValue();
    key = ExecuteQueryRequestEncoder.keyNullValue();
    valueType = ValueType.NULL_VAL;

    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ExecuteQueryRequest setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getKey() {
    return key;
  }

  public ExecuteQueryRequest setKey(final long key) {
    this.key = key;
    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public ExecuteQueryRequest setValueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .partitionId(partitionId)
        .key(key)
        .valueType(valueType);
    return getLength();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    partitionId = bodyDecoder.partitionId();
    key = bodyDecoder.key();
    valueType = bodyDecoder.valueType();
  }
}
