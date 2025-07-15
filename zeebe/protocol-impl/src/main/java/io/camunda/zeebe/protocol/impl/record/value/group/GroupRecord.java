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
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class GroupRecord extends UnifiedRecordValue implements GroupRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue GROUP_KEY_KEY = new StringValue("groupKey");
  private static final StringValue GROUP_ID_KEY = new StringValue("groupId");
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue DESCRIPTION_KEY = new StringValue("description");
  private static final StringValue ENTITY_ID_KEY = new StringValue("entityId");
  private static final StringValue ENTITY_TYPE_KEY = new StringValue("entityType");

  private final LongProperty groupKeyProp = new LongProperty(GROUP_KEY_KEY, -1L);
  private final StringProperty groupId = new StringProperty(GROUP_ID_KEY);
  private final StringProperty nameProp = new StringProperty(NAME_KEY, "");
  private final StringProperty descriptionProp = new StringProperty(DESCRIPTION_KEY, "");
  private final StringProperty entityIdProp = new StringProperty(ENTITY_ID_KEY, "");
  private final EnumProperty<EntityType> entityTypeProp =
      new EnumProperty<>(ENTITY_TYPE_KEY, EntityType.class, EntityType.UNSPECIFIED);

  public GroupRecord() {
    super(6);
    declareProperty(groupKeyProp)
        .declareProperty(groupId)
        .declareProperty(nameProp)
        .declareProperty(descriptionProp)
        .declareProperty(entityIdProp)
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
    if (description == null) {
      descriptionProp.reset();
      return this;
    }

    descriptionProp.setValue(description);
    return this;
  }

  @Override
  public String getEntityId() {
    return BufferUtil.bufferAsString(entityIdProp.getValue());
  }

  public GroupRecord setEntityId(final String entityId) {
    entityIdProp.setValue(entityId);
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
