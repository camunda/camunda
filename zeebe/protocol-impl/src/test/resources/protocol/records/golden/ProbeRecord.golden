/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.probe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobSecretReference;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ProbeRecord extends UnifiedRecordValue implements ProbeRecordValue {

  private final StringProperty probeIdProp = new StringProperty("probeId", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty inputExpressionProp = new StringProperty("inputExpression", "");
  private final DocumentProperty inputProp = new DocumentProperty("input");
  private final ArrayProperty<JobSecretReference> secretReferencesProp =
      new ArrayProperty<>("secretReferences", JobSecretReference::new);
  private final LongProperty requestTimeoutProp = new LongProperty("requestTimeout", 0L);
  private final LongProperty deadlineProp = new LongProperty("deadline", -1L);
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final IntegerProperty yieldCountProp = new IntegerProperty("yieldCount", 0);
  private final EnumProperty<ProbeOutcome> outcomeProp =
      new EnumProperty<>("outcome", ProbeOutcome.class, ProbeOutcome.UNSPECIFIED);
  private final DocumentProperty dataProp = new DocumentProperty("data");
  private final StringProperty messageProp = new StringProperty("message", "");
  private final EnumProperty<ProbeEndReason> endReasonProp =
      new EnumProperty<>("endReason", ProbeEndReason.class, ProbeEndReason.UNSPECIFIED);

  public ProbeRecord() {
    super(13);
    declareProperty(probeIdProp)
        .declareProperty(tenantIdProp)
        .declareProperty(inputExpressionProp)
        .declareProperty(inputProp)
        .declareProperty(secretReferencesProp)
        .declareProperty(requestTimeoutProp)
        .declareProperty(deadlineProp)
        .declareProperty(workerProp)
        .declareProperty(yieldCountProp)
        .declareProperty(outcomeProp)
        .declareProperty(dataProp)
        .declareProperty(messageProp)
        .declareProperty(endReasonProp);
  }

  /** Copies the whole record, so a caller can change the copy without touching the original. */
  public ProbeRecord copy() {
    final var copy = new ProbeRecord();
    copy.copyFrom(this);
    return copy;
  }

  /** Replaces this record's content with a copy of the other record's content. */
  public void copyFrom(final ProbeRecord other) {
    final var bytes = new byte[other.getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    other.write(buffer, 0);
    wrap(buffer, 0, bytes.length);
  }

  @Override
  public String getProbeId() {
    return BufferUtil.bufferAsString(probeIdProp.getValue());
  }

  public ProbeRecord setProbeId(final String probeId) {
    probeIdProp.setValue(probeId);
    return this;
  }

  @Override
  public String getInputExpression() {
    return BufferUtil.bufferAsString(inputExpressionProp.getValue());
  }

  public ProbeRecord setInputExpression(final String inputExpression) {
    inputExpressionProp.setValue(inputExpression);
    return this;
  }

  @Override
  public Map<String, Object> getInput() {
    return MsgPackConverter.convertToMap(inputProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getInputBuffer() {
    return inputProp.getValue();
  }

  public ProbeRecord setInput(final DirectBuffer input) {
    inputProp.setValue(input);
    return this;
  }

  public ProbeRecord resetInput() {
    inputProp.reset();
    return this;
  }

  @Override
  public List<JobSecretReferenceValue> getSecretReferences() {
    return secretReferencesProp.stream()
        .map(
            element -> {
              final var copy = new JobSecretReference();
              copy.copy(element);
              return (JobSecretReferenceValue) copy;
            })
        .toList();
  }

  @JsonIgnore
  public ValueArray<JobSecretReference> secretReferences() {
    return secretReferencesProp;
  }

  @JsonIgnore
  public boolean hasSecretReferences() {
    return !secretReferencesProp.isEmpty();
  }

  public ProbeRecord addSecretReference(
      final String storeId, final String secretReference, final String path) {
    secretReferencesProp
        .add()
        .setStoreId(storeId)
        .setSecretReference(secretReference)
        .setPath(path);
    return this;
  }

  public ProbeRecord resetSecretReferences() {
    secretReferencesProp.reset();
    return this;
  }

  @Override
  public long getRequestTimeout() {
    return requestTimeoutProp.getValue();
  }

  public ProbeRecord setRequestTimeout(final long requestTimeout) {
    requestTimeoutProp.setValue(requestTimeout);
    return this;
  }

  @Override
  public long getDeadline() {
    return deadlineProp.getValue();
  }

  public ProbeRecord setDeadline(final long deadline) {
    deadlineProp.setValue(deadline);
    return this;
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(workerProp.getValue());
  }

  public ProbeRecord setWorker(final String worker) {
    workerProp.setValue(worker);
    return this;
  }

  @Override
  public int getYieldCount() {
    return yieldCountProp.getValue();
  }

  public ProbeRecord setYieldCount(final int yieldCount) {
    yieldCountProp.setValue(yieldCount);
    return this;
  }

  @Override
  public ProbeOutcome getOutcome() {
    return outcomeProp.getValue();
  }

  public ProbeRecord setOutcome(final ProbeOutcome outcome) {
    outcomeProp.setValue(outcome);
    return this;
  }

  @Override
  public Map<String, Object> getData() {
    return MsgPackConverter.convertToMap(dataProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getDataBuffer() {
    return dataProp.getValue();
  }

  public ProbeRecord setData(final DirectBuffer data) {
    dataProp.setValue(data);
    return this;
  }

  @Override
  public String getMessage() {
    return BufferUtil.bufferAsString(messageProp.getValue());
  }

  public ProbeRecord setMessage(final String message) {
    messageProp.setValue(message);
    return this;
  }

  @Override
  public ProbeEndReason getEndReason() {
    return endReasonProp.getValue();
  }

  public ProbeRecord setEndReason(final ProbeEndReason endReason) {
    endReasonProp.setValue(endReason);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ProbeRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
