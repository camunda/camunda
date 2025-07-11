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
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import org.agrona.DirectBuffer;

public final class TenantRecord extends UnifiedRecordValue implements TenantRecordValue {
  private static final long DEFAULT_KEY = -1;

  // Static StringValue keys to avoid memory waste
  private static final StringValue TENANT_KEY_KEY = new StringValue("tenantKey");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue DESCRIPTION_KEY = new StringValue("description");
  private static final StringValue ENTITY_ID_KEY = new StringValue("entityId");
  private static final StringValue ENTITY_TYPE_KEY = new StringValue("entityType");

  private final LongProperty tenantKeyProp = new LongProperty(TENANT_KEY_KEY, DEFAULT_KEY);
  private final StringProperty tenantIdProp = new StringProperty(TENANT_ID_KEY);
  private final StringProperty nameProp = new StringProperty(NAME_KEY, "");
  private final StringProperty descriptionProp = new StringProperty(DESCRIPTION_KEY, "");
  private final StringProperty entityIdProp = new StringProperty(ENTITY_ID_KEY, "");
  private final EnumProperty<EntityType> entityTypeProp =
      new EnumProperty<>(ENTITY_TYPE_KEY, EntityType.class, EntityType.UNSPECIFIED);

  public TenantRecord() {
    super(6);
    declareProperty(tenantKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(nameProp)
        .declareProperty(descriptionProp)
        .declareProperty(entityIdProp)
        .declareProperty(entityTypeProp);
  }

  public TenantRecord copy() {
    final TenantRecord copy = new TenantRecord();
    copy.copyFrom(this);
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
  public String getDescription() {
    return bufferAsString(descriptionProp.getValue());
  }

  public TenantRecord setDescription(final String description) {
    if (description == null) {
      descriptionProp.reset();
      return this;
    }

    descriptionProp.setValue(description);
    return this;
  }

  @Override
  public String getEntityId() {
    return bufferAsString(entityIdProp.getValue());
  }

  public TenantRecord setEntityId(final String entityId) {
    entityIdProp.setValue(entityId);
    return this;
  }

  @Override
  public EntityType getEntityType() {
    return entityTypeProp.getValue();
  }

  public TenantRecord setEntityType(final EntityType entityType) {
    entityTypeProp.setValue(entityType);
    return this;
  }

  public boolean hasTenantKey() {
    return tenantKeyProp.getValue() != -1L;
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
