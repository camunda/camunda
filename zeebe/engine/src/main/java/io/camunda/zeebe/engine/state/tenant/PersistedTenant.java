/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.tenant;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class PersistedTenant extends UnpackedObject implements DbValue {

  private final LongProperty tenantKeyProp = new LongProperty("tenantKey");
  private final StringProperty tenantIdProp = new StringProperty("tenantId");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty descriptionProp = new StringProperty("description", "");

  public PersistedTenant() {
    super(4);
    declareProperty(tenantKeyProp);
    declareProperty(tenantIdProp);
    declareProperty(nameProp);
    declareProperty(descriptionProp);
  }

  public PersistedTenant copy() {
    final var copy = new PersistedTenant();
    copy.copyFrom(this);
    return copy;
  }

  /**
   * Gets the tenant key.
   *
   * @return the tenant key as a long value
   */
  public long getTenantKey() {
    return tenantKeyProp.getValue();
  }

  /**
   * Sets the tenant key.
   *
   * @param tenantKey the tenant key to set
   * @return the current PersistedTenant instance
   */
  public PersistedTenant setTenantKey(final long tenantKey) {
    tenantKeyProp.setValue(tenantKey);
    return this;
  }

  /**
   * Gets the tenant ID.
   *
   * @return the tenant ID as a string
   */
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  /**
   * Sets the tenant ID.
   *
   * @param tenantId the tenant ID to set
   * @return the current PersistedTenant instance
   */
  public PersistedTenant setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  /**
   * Gets the tenant name.
   *
   * @return the name of the tenant as a string
   */
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  /**
   * Sets the tenant name.
   *
   * @param name the tenant name to set
   * @return the current PersistedTenant instance
   */
  public PersistedTenant setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public String getDescription() {
    return BufferUtil.bufferAsString(descriptionProp.getValue());
  }

  public PersistedTenant setDescription(final String description) {
    descriptionProp.setValue(description);
    return this;
  }

  /**
   * Wraps the provided TenantRecord into this PersistedTenant instance. Copies the tenant key,
   * tenant ID, and tenant name from the TenantRecord to the corresponding properties of this
   * persisted tenant, allowing the current instance to reflect the state of the provided record.
   *
   * @param tenantRecord the TenantRecord from which to copy the data
   */
  public void from(final TenantRecord tenantRecord) {
    tenantKeyProp.setValue(tenantRecord.getTenantKey());
    tenantIdProp.setValue(tenantRecord.getTenantId());
    nameProp.setValue(tenantRecord.getName());
    descriptionProp.setValue(tenantRecord.getDescription());
  }
}
