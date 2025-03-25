/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class PersistedRole extends UnpackedObject implements DbValue {

  private final LongProperty roleKeyProp = new LongProperty("roleKey");
  private final StringProperty roleIdProp = new StringProperty("roleId");
  private final StringProperty descriptionProp = new StringProperty("description");
  private final StringProperty nameProp = new StringProperty("name");

  public PersistedRole() {
    super(4);
    declareProperty(roleKeyProp)
        .declareProperty(roleIdProp)
        .declareProperty(nameProp)
        .declareProperty(descriptionProp);
  }

  public long getRoleKey() {
    return roleKeyProp.getValue();
  }

  public PersistedRole setRoleKey(final long roleKey) {
    roleKeyProp.setValue(roleKey);
    return this;
  }

  public String getRoleId() {
    return BufferUtil.bufferAsString(roleIdProp.getValue());
  }

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public PersistedRole setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public String getDescription() {
    return BufferUtil.bufferAsString(descriptionProp.getValue());
  }

  public PersistedRole copy() {
    final var copy = new PersistedRole();
    copy.copyFrom(this);
    return copy;
  }

  /**
   * Wraps the provided RoleRecord into this PersistedRole instance. Copies the properties from the
   * TenantRecord to the corresponding properties of this persisted tenant, allowing the current
   * instance to reflect the state of the provided record.
   *
   * @param roleRecord the RoleRecord from which to copy the data
   */
  public void from(final RoleRecord roleRecord) {
    roleKeyProp.setValue(roleRecord.getRoleKey());
    roleIdProp.setValue(roleRecord.getRoleId());
    nameProp.setValue(roleRecord.getName());
    descriptionProp.setValue(roleRecord.getDescription());
  }
}
