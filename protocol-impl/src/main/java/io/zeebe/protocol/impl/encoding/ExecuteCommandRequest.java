/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.encoding;

import static io.zeebe.protocol.record.ExecuteCommandRequestEncoder.keyNullValue;
import static io.zeebe.protocol.record.ExecuteCommandRequestEncoder.partitionIdNullValue;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.tracing.SbeTracingAdapter;
import io.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.record.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.protocol.record.MessageHeaderEncoder;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExecuteCommandRequest implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ExecuteCommandRequestEncoder bodyEncoder = new ExecuteCommandRequestEncoder();
  private final ExecuteCommandRequestDecoder bodyDecoder = new ExecuteCommandRequestDecoder();
  private final DirectBuffer value = new UnsafeBuffer(0, 0);
  private final DirectBuffer spanContext = new UnsafeBuffer(0, 0);
  private final SbeTracingAdapter spanContextAdapter = new SbeTracingAdapter(spanContext);

  private int partitionId;
  private long key;
  private ValueType valueType;
  private Intent intent;

  public ExecuteCommandRequest() {
    reset();
  }

  public ExecuteCommandRequest reset() {
    partitionId = partitionIdNullValue();
    key = keyNullValue();
    valueType = ValueType.NULL_VAL;
    intent = Intent.UNKNOWN;
    value.wrap(0, 0);
    spanContext.wrap(0, 0);

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

  public ExecuteCommandRequest setKey(final long key) {
    this.key = key;
    this.partitionId = Protocol.decodePartitionId(key);

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
    this.value.wrap(buffer, offset, length);
    return this;
  }

  public SbeTracingAdapter getSpanContextAdapter() {
    return spanContextAdapter;
  }

  public DirectBuffer getSpanContext() {
    return spanContext;
  }

  public ExecuteCommandRequest setSpanContext(
      final DirectBuffer buffer, final int offset, final int length) {
    this.spanContext.wrap(buffer, offset, length);
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
    valueType = bodyDecoder.valueType();
    intent = Intent.fromProtocolValue(valueType, bodyDecoder.intent());

    offset += bodyDecoder.sbeBlockLength();

    final int valueLength = bodyDecoder.valueLength();
    offset += ExecuteCommandRequestDecoder.valueHeaderLength();

    value.wrap(buffer, offset, valueLength);
    offset += valueLength;
    bodyDecoder.limit(offset);

    final int spanContextLength = bodyDecoder.spanContextLength();
    offset += ExecuteCommandRequestDecoder.spanContextHeaderLength();

    if (spanContextLength > 0) {
      spanContext.wrap(buffer, offset, spanContextLength);
      offset += spanContextLength;
    }

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
        + ExecuteCommandRequestEncoder.spanContextHeaderLength()
        + spanContext.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
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
        .putValue(value, 0, value.capacity())
        .putSpanContext(spanContext, 0, spanContext.capacity());
  }
}
