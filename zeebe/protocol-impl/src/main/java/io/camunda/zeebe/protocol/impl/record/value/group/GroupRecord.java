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
  // TODO remove default empty string https://github.com/camunda/camunda/issues/30139
  private final StringProperty groupId = new StringProperty("groupId", "");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty descriptionProp = new StringProperty("description", "");
  private final LongProperty entityKeyProp = new LongProperty("entityKey", -1L);
  private final EnumProperty<EntityType> entityTypeProp =
      new EnumProperty<>("entityType", EntityType.class, EntityType.UNSPECIFIED);

  public GroupRecord() {
    super(6);
    declareProperty(groupKeyProp)
        .declareProperty(groupId)
        .declareProperty(nameProp)
        .declareProperty(descriptionProp)
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
  public String getGroupId() {
    return BufferUtil.bufferAsString(groupId.getValue());
  }

  public GroupRecord setGroupId(final String groupId) {
    this.groupId.setValue(groupId);
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
  public String getDescription() {
    return BufferUtil.bufferAsString(descriptionProp.getValue());
  }

  public GroupRecord setDescription(final String description) {
    descriptionProp.setValue(description);
    return this;
  }

  @Override
  public long getEntityKey() {
    return entityKeyProp.getValue();
  }

  public GroupRecord setEntityKey(final long entityKey) {
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
