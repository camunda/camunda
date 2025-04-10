/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class RoleRecord extends UnifiedRecordValue implements RoleRecordValue {

  private final LongProperty roleKeyProp = new LongProperty("roleKey", -1L);
  // TODO remove default empty string https://github.com/camunda/camunda/issues/30140
  private final StringProperty roleIdProp = new StringProperty("roleId", "");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty descriptionProp = new StringProperty("description", "");
  // TODO remove entityKeyProp https://github.com/camunda/camunda/issues/30116
  private final LongProperty entityKeyProp = new LongProperty("entityKey", -1L);
  private final StringProperty entityIdProp = new StringProperty("entityId", "");
  private final EnumProperty<EntityType> entityTypeProp =
      new EnumProperty<>("entityType", EntityType.class, EntityType.UNSPECIFIED);

  public RoleRecord() {
    super(7);
    declareProperty(roleKeyProp)
        .declareProperty(roleIdProp)
        .declareProperty(nameProp)
        .declareProperty(descriptionProp)
        .declareProperty(entityKeyProp)
        .declareProperty(entityIdProp)
        .declareProperty(entityTypeProp);
  }

  @Override
  public long getRoleKey() {
    return roleKeyProp.getValue();
  }

  public RoleRecord setRoleKey(final long roleKey) {
    roleKeyProp.setValue(roleKey);
    return this;
  }

  @Override
  public String getRoleId() {
    return BufferUtil.bufferAsString(roleIdProp.getValue());
  }

  public RoleRecord setRoleId(final String roleId) {
    roleIdProp.setValue(roleId);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public RoleRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getDescription() {
    return BufferUtil.bufferAsString(descriptionProp.getValue());
  }

  public RoleRecord setDescription(final String description) {
    descriptionProp.setValue(description);
    return this;
  }

  @Override
  public long getEntityKey() {
    return entityKeyProp.getValue();
  }

  @Override
  public String getEntityId() {
    return BufferUtil.bufferAsString(entityIdProp.getValue());
  }

  public RoleRecord setEntityId(final String entityId) {
    entityIdProp.setValue(entityId);
    return this;
  }

  @Override
  public EntityType getEntityType() {
    return entityTypeProp.getValue();
  }

  public RoleRecord setEntityType(final EntityType entityType) {
    entityTypeProp.setValue(entityType);
    return this;
  }

  public RoleRecord setEntityKey(final long entityKey) {
    entityKeyProp.setValue(entityKey);
    return this;
  }
}
