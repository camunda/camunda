/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue.MessageStartLockReleaseHolderValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * A single holder entry of a {@link MessageStartCorrelationKeyLockReleaseRecord}: one
 * correlation-key lock on {@code P_K} whose holder instance lives on {@code P_B}. See {@link
 * MessageStartLockReleaseHolderValue} for the field semantics.
 */
@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class MessageStartLockReleaseHolder extends ObjectValue
    implements MessageStartLockReleaseHolderValue {

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public MessageStartLockReleaseHolder() {
    super(4);
    declareProperty(processInstanceKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public MessageStartLockReleaseHolder setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public MessageStartLockReleaseHolder setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public MessageStartLockReleaseHolder setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @Override
  public String getCorrelationKey() {
    return BufferUtil.bufferAsString(correlationKeyProp.getValue());
  }

  public MessageStartLockReleaseHolder setCorrelationKey(final String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageStartLockReleaseHolder setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public MessageStartLockReleaseHolder setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public MessageStartLockReleaseHolder setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public void copy(final MessageStartLockReleaseHolder other) {
    setProcessInstanceKey(other.getProcessInstanceKey());
    setBpmnProcessId(other.getBpmnProcessIdBuffer());
    setCorrelationKey(other.getCorrelationKeyBuffer());
    setTenantId(other.getTenantId());
  }
}
