/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.group;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class PersistedGroup extends UnpackedObject implements DbValue {

  private final LongProperty groupKeyProp = new LongProperty("groupKey");
  private final StringProperty groupIdProp = new StringProperty("groupId");
  private final StringProperty descriptionProp = new StringProperty("description");
  private final StringProperty nameProp = new StringProperty("name");

  public PersistedGroup() {
    super(4);
    declareProperty(groupKeyProp)
        .declareProperty(groupIdProp)
        .declareProperty(descriptionProp)
        .declareProperty(nameProp);
  }

  public void wrap(final GroupRecord group) {
    groupKeyProp.setValue(group.getGroupKey());
    groupIdProp.setValue(group.getGroupId());
    descriptionProp.setValue(group.getDescription());
    nameProp.setValue(group.getNameBuffer());
  }

  public long getGroupKey() {
    return groupKeyProp.getValue();
  }

  public PersistedGroup setGroupKey(final long groupKey) {
    groupKeyProp.setValue(groupKey);
    return this;
  }

  public String getGroupId() {
    return BufferUtil.bufferAsString(groupIdProp.getValue());
  }

  public PersistedGroup setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getDescription() {
    return BufferUtil.bufferAsString(descriptionProp.getValue());
  }

  public PersistedGroup setDescription(final String description) {
    descriptionProp.setValue(description);
    return this;
  }

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public PersistedGroup setName(final String name) {
    nameProp.setValue(name);
    return this;
  }
}
