/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.broker.protocol.brokerapi;

import io.camunda.zeebe.protocol.record.ExecuteCommandResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import io.camunda.zeebe.util.EnsureUtil;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExecuteCommandResponseWriter
    extends AbstractMessageBuilder<ExecuteCommandRequest> {
  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final ExecuteCommandResponseEncoder bodyEncoder = new ExecuteCommandResponseEncoder();
  protected final MsgPackHelper msgPackHelper;

  protected Function<ExecuteCommandRequest, Long> keyFunction = r -> r.key();
  protected Function<ExecuteCommandRequest, Integer> partitionIdFunction = r -> r.partitionId();
  protected Function<ExecuteCommandRequest, Map<String, Object>> eventFunction;
  protected long key;
  protected int partitionId;
  protected byte[] value;
  private Function<ExecuteCommandRequest, Intent> intentFunction = r -> r.intent();
  private RecordType recordType;
  private Intent intent;
  private ValueType valueType;
  private RejectionType rejectionType = RejectionType.NULL_VAL;
  private UnsafeBuffer rejectionReason = new UnsafeBuffer(0, 0);

  public ExecuteCommandResponseWriter(final MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  @Override
  public void initializeFrom(final ExecuteCommandRequest request) {
    key = keyFunction.apply(request);
    partitionId = partitionIdFunction.apply(request);
    final Map<String, Object> deserializedEvent = eventFunction.apply(request);
    value = msgPackHelper.encodeAsMsgPack(deserializedEvent);
    valueType = request.valueType();
    intent = intentFunction.apply(request);
  }

  public void setPartitionIdFunction(
      final Function<ExecuteCommandRequest, Integer> partitionIdFunction) {
    this.partitionIdFunction = partitionIdFunction;
  }

  public void setEventFunction(
      final Function<ExecuteCommandRequest, Map<String, Object>> eventFunction) {
    this.eventFunction = eventFunction;
  }

  public void setRecordType(final RecordType recordType) {
    this.recordType = recordType;
  }

  public void setKeyFunction(final Function<ExecuteCommandRequest, Long> keyFunction) {
    this.keyFunction = keyFunction;
  }

  public void setIntentFunction(final Function<ExecuteCommandRequest, Intent> intentFunction) {
    this.intentFunction = intentFunction;
  }

  public void setRejectionType(final RejectionType rejectionType) {
    this.rejectionType = rejectionType;
  }

  public void setRejectionReason(final String rejectionReason) {
    final byte[] bytes = rejectionReason.getBytes(StandardCharsets.UTF_8);
    this.rejectionReason = new UnsafeBuffer(bytes);
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ExecuteCommandResponseEncoder.BLOCK_LENGTH
        + ExecuteCommandResponseEncoder.valueHeaderLength()
        + value.length
        + ExecuteCommandResponseEncoder.rejectionReasonHeaderLength()
        + rejectionReason.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    EnsureUtil.ensureNotNull("recordType", recordType);
    EnsureUtil.ensureNotNull("valueType", valueType);
    EnsureUtil.ensureNotNull("intent", intent);

    // protocol message
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .recordType(recordType)
        .valueType(valueType)
        .intent(intent.value())
        .partitionId(partitionId)
        .key(key)
        .rejectionType(rejectionType)
        .putValue(value, 0, value.length)
        .putRejectionReason(rejectionReason, 0, rejectionReason.capacity());
    return bodyEncoder.encodedLength() + headerEncoder.encodedLength();
  }
}
