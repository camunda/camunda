/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.record.ExecuteCommandResponseEncoder;
import io.zeebe.protocol.record.MessageHeaderEncoder;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.EnsureUtil;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecuteCommandResponseWriter extends AbstractMessageBuilder<ExecuteCommandRequest> {
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

  public ExecuteCommandResponseWriter(MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  @Override
  public void initializeFrom(ExecuteCommandRequest request) {
    key = keyFunction.apply(request);
    partitionId = partitionIdFunction.apply(request);
    final Map<String, Object> deserializedEvent = eventFunction.apply(request);
    value = msgPackHelper.encodeAsMsgPack(deserializedEvent);
    this.valueType = request.valueType();
    this.intent = intentFunction.apply(request);
  }

  public void setPartitionIdFunction(Function<ExecuteCommandRequest, Integer> partitionIdFunction) {
    this.partitionIdFunction = partitionIdFunction;
  }

  public void setEventFunction(Function<ExecuteCommandRequest, Map<String, Object>> eventFunction) {
    this.eventFunction = eventFunction;
  }

  public void setRecordType(RecordType recordType) {
    this.recordType = recordType;
  }

  public void setKeyFunction(Function<ExecuteCommandRequest, Long> keyFunction) {
    this.keyFunction = keyFunction;
  }

  public void setIntentFunction(Function<ExecuteCommandRequest, Intent> intentFunction) {
    this.intentFunction = intentFunction;
  }

  public void setRejectionType(RejectionType rejectionType) {
    this.rejectionType = rejectionType;
  }

  public void setRejectionReason(String rejectionReason) {
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
  public void write(MutableDirectBuffer buffer, int offset) {
    EnsureUtil.ensureNotNull("recordType", recordType);
    EnsureUtil.ensureNotNull("valueType", valueType);
    EnsureUtil.ensureNotNull("intent", intent);

    // protocol header
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    // protocol message
    bodyEncoder
        .wrap(buffer, offset)
        .recordType(recordType)
        .valueType(valueType)
        .intent(intent.value())
        .partitionId(partitionId)
        .key(key)
        .rejectionType(rejectionType)
        .putValue(value, 0, value.length)
        .putRejectionReason(rejectionReason, 0, rejectionReason.capacity());
  }
}
