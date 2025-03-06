/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class GroupRecord extends UnifiedRecordValue implements GroupRecordValue {

  private final LongProperty groupKeyProp = new LongProperty("groupKey", -1L);
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty entityKeyProp = new StringProperty("entityKey", "");
  private final EnumProperty<EntityType> entityTypeProp =
      new EnumProperty<>("entityType", EntityType.class, EntityType.UNSPECIFIED);

  public GroupRecord() {
    super(4);
    declareProperty(groupKeyProp)
        .declareProperty(nameProp)
        .declareProperty(entityKeyProp)
        .declareProperty(entityTypeProp);
  }

  @Override
  public long getGroupKey() {
    return groupKeyProp.getValue();
  }

  public GroupRecord setGroupKey(final long groupKey) {
    groupKeyProp.setValue(groupKey);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public GroupRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getEntityKey() {
    return entityKeyProp.getValue();
  }

  public GroupRecord setEntityKey(final String entityKey) {
    entityKeyProp.setValue(entityKey);
    return this;
  }

  @Override
  public EntityType getEntityType() {
    return entityTypeProp.getValue();
  }

  public GroupRecord setEntityType(final EntityType entityType) {
    entityTypeProp.setValue(entityType);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }
}
