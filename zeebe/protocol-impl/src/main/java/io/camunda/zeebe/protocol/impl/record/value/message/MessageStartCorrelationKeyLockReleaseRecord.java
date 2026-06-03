/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.DirectBuffer;

/**
 * Implementation of {@link MessageStartCorrelationKeyLockReleaseRecordValue}.
 *
 * <p>This record drives the pull-based release lookup that lets {@code P_K = hash(correlationKey)}
 * discover when a message-start instance it created via the cross-partition handshake has completed
 * on {@code P_B = hash(businessId)}, so the correlation-key lock can be released. It never resides
 * on a single partition in isolation: {@code P_K} sends the {@code QUERY} to {@code P_B} (derived
 * from {@link #processInstanceKeyProp the holder instance key}); {@code P_B} replies {@code
 * RELEASE} back to {@code P_K} (derived from {@link #requestKeyProp the request key}) only when the
 * holder is gone.
 */
public final class MessageStartCorrelationKeyLockReleaseRecord extends UnifiedRecordValue
    implements MessageStartCorrelationKeyLockReleaseRecordValue {

  private final LongProperty requestKeyProp = new LongProperty("requestKey", -1L);
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public MessageStartCorrelationKeyLockReleaseRecord() {
    super(5);
    declareProperty(requestKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(tenantIdProp);
  }

  public void wrap(final MessageStartCorrelationKeyLockReleaseRecord record) {
    setRequestKey(record.getRequestKey());
    setProcessInstanceKey(record.getProcessInstanceKey());
    setBpmnProcessId(record.getBpmnProcessId());
    setCorrelationKey(record.getCorrelationKey());
    setTenantId(record.getTenantId());
  }

  @Override
  public long getRequestKey() {
    return requestKeyProp.getValue();
  }

  public MessageStartCorrelationKeyLockReleaseRecord setRequestKey(final long requestKey) {
    requestKeyProp.setValue(requestKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public MessageStartCorrelationKeyLockReleaseRecord setProcessInstanceKey(
      final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  public MessageStartCorrelationKeyLockReleaseRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public MessageStartCorrelationKeyLockReleaseRecord setBpmnProcessId(
      final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKeyProp.getValue());
  }

  public MessageStartCorrelationKeyLockReleaseRecord setCorrelationKey(
      final String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageStartCorrelationKeyLockReleaseRecord setCorrelationKey(
      final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public MessageStartCorrelationKeyLockReleaseRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public MessageStartCorrelationKeyLockReleaseRecord setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
