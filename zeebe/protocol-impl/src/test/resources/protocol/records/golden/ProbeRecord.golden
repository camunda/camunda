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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
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
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProbeRecord extends UnifiedRecordValue implements ProbeRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue PROBE_ID_KEY = new StringValue("probeId");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue INPUT_EXPRESSION_KEY = new StringValue("inputExpression");
  private static final StringValue INPUT_KEY = new StringValue("input");
  private static final StringValue SECRET_REFERENCES_KEY = new StringValue("secretReferences");
  private static final StringValue REQUEST_TIMEOUT_KEY = new StringValue("requestTimeout");
  private static final StringValue DEADLINE_KEY = new StringValue("deadline");
  private static final StringValue WORKER_KEY = new StringValue("worker");
  private static final StringValue OUTCOME_KEY = new StringValue("outcome");
  private static final StringValue DATA_KEY = new StringValue("data");
  private static final StringValue MESSAGE_KEY = new StringValue("message");
  private static final StringValue END_REASON_KEY = new StringValue("endReason");

  private final StringProperty probeIdProp = new StringProperty(PROBE_ID_KEY, "");
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty inputExpressionProp = new StringProperty(INPUT_EXPRESSION_KEY, "");
  private final DocumentProperty inputProp = new DocumentProperty(INPUT_KEY);
  private final ArrayProperty<JobSecretReference> secretReferencesProp =
      new ArrayProperty<>(SECRET_REFERENCES_KEY, JobSecretReference::new);
  private final LongProperty requestTimeoutProp = new LongProperty(REQUEST_TIMEOUT_KEY, 0L);
  private final LongProperty deadlineProp = new LongProperty(DEADLINE_KEY, -1L);
  private final StringProperty workerProp = new StringProperty(WORKER_KEY, "");
  private final EnumProperty<ProbeOutcome> outcomeProp =
      new EnumProperty<>(OUTCOME_KEY, ProbeOutcome.class, ProbeOutcome.UNSPECIFIED);
  private final DocumentProperty dataProp = new DocumentProperty(DATA_KEY);
  private final StringProperty messageProp = new StringProperty(MESSAGE_KEY, "");
  private final EnumProperty<ProbeEndReason> endReasonProp =
      new EnumProperty<>(END_REASON_KEY, ProbeEndReason.class, ProbeEndReason.UNSPECIFIED);

  public ProbeRecord() {
    super(12);
    declareProperty(probeIdProp)
        .declareProperty(tenantIdProp)
        .declareProperty(inputExpressionProp)
        .declareProperty(inputProp)
        .declareProperty(secretReferencesProp)
        .declareProperty(requestTimeoutProp)
        .declareProperty(deadlineProp)
        .declareProperty(workerProp)
        .declareProperty(outcomeProp)
        .declareProperty(dataProp)
        .declareProperty(messageProp)
        .declareProperty(endReasonProp);
  }

  /**
   * Replaces this record's content with a copy of the other record's. Unlike the buffer setters,
   * which only point at the given buffer, the copy owns its bytes: it stays valid when the other
   * record is reused, for example when it is backed by a state read.
   */
  public ProbeRecord copyFrom(final ProbeRecord record) {
    final var bytes = new byte[record.getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    record.write(buffer, 0);
    wrap(buffer, 0, bytes.length);
    return this;
  }

  @Override
  public String getProbeId() {
    return BufferUtil.bufferAsString(probeIdProp.getValue());
  }

  public ProbeRecord setProbeId(final String probeId) {
    probeIdProp.setValue(probeId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getProbeIdBuffer() {
    return probeIdProp.getValue();
  }

  @Override
  public String getInputExpression() {
    return BufferUtil.bufferAsString(inputExpressionProp.getValue());
  }

  public ProbeRecord setInputExpression(final String inputExpression) {
    inputExpressionProp.setValue(inputExpression);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getInputExpressionBuffer() {
    return inputExpressionProp.getValue();
  }

  @Override
  public Map<String, Object> getInput() {
    return MsgPackConverter.convertToMap(inputProp.getValue());
  }

  public ProbeRecord setInput(final DirectBuffer input) {
    inputProp.setValue(input);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getInputBuffer() {
    return inputProp.getValue();
  }

  @Override
  public List<JobSecretReferenceValue> getSecretReferences() {
    // detach copies so the returned list stays valid if this record is reused or reset later
    return secretReferencesProp.stream()
        .map(
            element -> {
              final var copy = new JobSecretReference();
              copy.copy(element);
              return (JobSecretReferenceValue) copy;
            })
        .toList();
  }

  /**
   * Direct access to the secret reference elements, without the detached copies of {@link
   * #getSecretReferences()}. The elements stay owned by this record; do not hold on to them.
   */
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

  public ProbeRecord setWorker(final DirectBuffer worker) {
    workerProp.setValue(worker);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return workerProp.getValue();
  }

  @Override
  public ProbeOutcome getOutcome() {
    // never null: the property has a default
    return Objects.requireNonNull(outcomeProp.getValue());
  }

  public ProbeRecord setOutcome(final ProbeOutcome outcome) {
    outcomeProp.setValue(outcome);
    return this;
  }

  @Override
  public Map<String, Object> getData() {
    return MsgPackConverter.convertToMap(dataProp.getValue());
  }

  public ProbeRecord setData(final DirectBuffer data) {
    dataProp.setValue(data);
    return this;
  }

  /**
   * Empties the data. The answer's data belongs on the {@code ANSWER} command only: an event that
   * is built from that command must reset it, so the data never reaches an exported event.
   */
  public ProbeRecord resetData() {
    dataProp.reset();
    return this;
  }

  @JsonIgnore
  public DirectBuffer getDataBuffer() {
    return dataProp.getValue();
  }

  @Override
  public String getMessage() {
    return BufferUtil.bufferAsString(messageProp.getValue());
  }

  public ProbeRecord setMessage(final String message) {
    messageProp.setValue(message);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getMessageBuffer() {
    return messageProp.getValue();
  }

  @Override
  public ProbeEndReason getEndReason() {
    // never null: the property has a default
    return Objects.requireNonNull(endReasonProp.getValue());
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

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }
}
