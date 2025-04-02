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
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PersistedGroup extends UnpackedObject implements DbValue {

  private final LongProperty groupKeyProp = new LongProperty("groupKey");
  private final StringProperty groupIdProp = new StringProperty("groupId");
  private final StringProperty nameProp = new StringProperty("name");
  private final ArrayProperty<StringValue> tenantIdsProp =
      new ArrayProperty<>("tenantIds", StringValue::new);

  public PersistedGroup() {
    super(4);
    declareProperty(groupKeyProp)
        .declareProperty(groupIdProp)
        .declareProperty(nameProp)
        .declareProperty(tenantIdsProp);
  }

  public void wrap(final GroupRecord group) {
    groupKeyProp.setValue(group.getGroupKey());
    groupIdProp.setValue(group.getGroupId());
    nameProp.setValue(group.getNameBuffer());
    tenantIdsProp.reset();
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

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public PersistedGroup setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public List<String> getTenantIdsList() {
    return StreamSupport.stream(tenantIdsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  public PersistedGroup setTenantIdsList(final List<String> tenantIds) {
    tenantIdsProp.reset();
    tenantIds.forEach(tenantId -> tenantIdsProp.add().wrap(BufferUtil.wrapString(tenantId)));
    return this;
  }

  public PersistedGroup addTenantId(final String tenantId) {
    tenantIdsProp.add().wrap(BufferUtil.wrapString(tenantId));
    return this;
  }
}
