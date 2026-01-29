/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.engine.state.ObjectDbValue;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.protocol.record.value.EntityType;

public class EntityTypeValue extends ObjectDbValue {

  private final EnumProperty<EntityType> entityTypeProp =
      new EnumProperty<>("entityType", EntityType.class);

  public EntityTypeValue() {
    super(1);
    declareProperty(entityTypeProp);
  }

  public EntityType getEntityType() {
    return entityTypeProp.getValue();
  }

  public void setEntityType(final EntityType entityType) {
    entityTypeProp.setValue(entityType);
  }
}
