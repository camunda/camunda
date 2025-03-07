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
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty entityKeyProp = new StringProperty("entityKey", "");
  private final EnumProperty<EntityType> entityTypeProp =
      new EnumProperty<>("entityType", EntityType.class, EntityType.UNSPECIFIED);

  public RoleRecord() {
    super(4);
    declareProperty(roleKeyProp)
        .declareProperty(nameProp)
        .declareProperty(entityKeyProp)
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
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public RoleRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getEntityKey() {
    return BufferUtil.bufferAsString(entityKeyProp.getValue());
  }

  @Override
  public EntityType getEntityType() {
    return entityTypeProp.getValue();
  }

  public RoleRecord setEntityType(final EntityType entityType) {
    entityTypeProp.setValue(entityType);
    return this;
  }

  public RoleRecord setEntityKey(final String entityKey) {
    entityKeyProp.setValue(entityKey);
    return this;
  }
}
