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
import io.camunda.zeebe.msgpack.property.EnumProperty;


//todo its just like mock for implements tenant, should be implemented in the https://github.com/camunda/camunda/issues/22913
public class EntityTypeValue extends UnpackedObject implements DbValue {

  private final EnumProperty<EntityType> entityType = new EnumProperty<>("entityType", EntityType.class);

  public EntityTypeValue() {
    super(1);
    declareProperty(entityType);
  }

  public EntityType getEntityType() {
    return entityType.getValue();
  }

  public void setEntityType(final EntityType type) {
    entityType.setValue(type);
  }

  public enum EntityType {
    USER, ROLE, OTHER;
  }
}
