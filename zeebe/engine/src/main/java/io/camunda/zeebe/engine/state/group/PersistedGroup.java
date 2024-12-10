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
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PersistedGroup extends UnpackedObject implements DbValue {

  private final LongProperty groupKeyProp = new LongProperty("groupKey");
  private final StringProperty nameProp = new StringProperty("name");
  private final ArrayProperty<LongValue> tenantKeysProp =
      new ArrayProperty<>("tenantKeys", LongValue::new);

  public PersistedGroup() {
    super(3);
    declareProperty(groupKeyProp).declareProperty(nameProp).declareProperty(tenantKeysProp);
  }

  public long getGroupKey() {
    return groupKeyProp.getValue();
  }

  public PersistedGroup setGroupKey(final long groupKey) {
    groupKeyProp.setValue(groupKey);
    return this;
  }

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public PersistedGroup setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public List<Long> getTenantKeys() {
    return StreamSupport.stream(tenantKeysProp.spliterator(), false)
        .map(LongValue::getValue)
        .collect(Collectors.toList());
  }

  public PersistedGroup setTenantKeys(final List<Long> tenantKeys) {
    tenantKeysProp.reset();
    tenantKeys.forEach(tenantKey -> tenantKeysProp.add().setValue(tenantKey));
    return this;
  }

  public PersistedGroup addTenantKey(final long tenantKey) {
    tenantKeysProp.add().setValue(tenantKey);
    return this;
  }
}
