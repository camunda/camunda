/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.tenant;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import org.agrona.DirectBuffer;

public final class TenantRecord extends UnifiedRecordValue implements TenantRecordValue {
  private final LongProperty tenantKeyProp = new LongProperty("tenantKey", -1L);
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final LongProperty entityKeyProp = new LongProperty("entityKey", -1L);

  public TenantRecord() {
    super(4);
    declareProperty(tenantKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(nameProp)
        .declareProperty(entityKeyProp);
  }

  public void wrap(final TenantRecord record) {
    tenantKeyProp.setValue(record.getTenantKey());
    tenantIdProp.setValue(record.getTenantIdBuffer());
    nameProp.setValue(record.getNameBuffer());
    entityKeyProp.setValue(record.getEntityKey());
  }

  public TenantRecord copy() {
    final TenantRecord copy = new TenantRecord();
    copy.tenantKeyProp.setValue(getTenantKey());
    copy.tenantIdProp.setValue(tenantIdProp.getValue());
    copy.nameProp.setValue(nameProp.getValue());
    copy.entityKeyProp.setValue(getEntityKey());
    return copy;
  }

  @Override
  public long getTenantKey() {
    return tenantKeyProp.getValue();
  }

  public TenantRecord setTenantKey(final long tenantKey) {
    tenantKeyProp.setValue(tenantKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public TenantRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public TenantRecord setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  public TenantRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public TenantRecord setName(final DirectBuffer name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public long getEntityKey() {
    return entityKeyProp.getValue();
  }

  public TenantRecord setEntityKey(final long entityKey) {
    entityKeyProp.setValue(entityKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }
}
