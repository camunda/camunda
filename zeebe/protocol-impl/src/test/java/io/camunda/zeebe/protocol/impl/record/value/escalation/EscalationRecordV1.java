/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.escalation;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EscalationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class EscalationRecordV1 extends UnifiedRecordValue implements EscalationRecordValue {

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty escalationCodeProp = new StringProperty("escalationCode", "");
  private final StringProperty throwElementIdProp = new StringProperty("throwElementId", "");
  private final StringProperty catchElementIdProp = new StringProperty("catchElementId", "");
  private final IntegerProperty storageOrdinalProp = new IntegerProperty("storageOrdinal", 0);

  public EscalationRecordV1() {
    super(5);
    declareProperty(processInstanceKeyProp)
        .declareProperty(escalationCodeProp)
        .declareProperty(throwElementIdProp)
        .declareProperty(catchElementIdProp)
        .declareProperty(storageOrdinalProp);
  }

  public void wrap(final EscalationRecordV1 record) {
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    escalationCodeProp.setValue(record.getEscalationCode());
    throwElementIdProp.setValue(record.getThrowElementId());
    catchElementIdProp.setValue(record.getCatchElementId());
    storageOrdinalProp.setValue(record.getStorageOrdinal());
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  @Override
  public String getEscalationCode() {
    return BufferUtil.bufferAsString(escalationCodeProp.getValue());
  }

  public EscalationRecordV1 setEscalationCode(final String escalationCode) {
    escalationCodeProp.setValue(escalationCode);
    return this;
  }

  @Override
  public String getThrowElementId() {
    return BufferUtil.bufferAsString(throwElementIdProp.getValue());
  }

  @Override
  public String getCatchElementId() {
    return BufferUtil.bufferAsString(catchElementIdProp.getValue());
  }

  public EscalationRecordV1 setCatchElementId(final DirectBuffer catchElementId) {
    catchElementIdProp.setValue(catchElementId);
    return this;
  }

  public EscalationRecordV1 setThrowElementId(final DirectBuffer throwElementId) {
    throwElementIdProp.setValue(throwElementId);
    return this;
  }

  public EscalationRecordV1 setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public EscalationRecordV1 setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }

  @Override
  public String getTenantId() {
    // todo(#13774): replace dummy implementation
    return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  }
}
