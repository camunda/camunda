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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class PersistedRole extends UnpackedObject implements DbValue {

  private final LongProperty roleKeyProp = new LongProperty("roleKey");
  private final StringProperty nameProp = new StringProperty("name");

  public PersistedRole() {
    super(2);
    declareProperty(roleKeyProp);
    declareProperty(nameProp);
  }

  public long getRoleKey() {
    return roleKeyProp.getValue();
  }

  public PersistedRole setRoleKey(final long roleKey) {
    roleKeyProp.setValue(roleKey);
    return this;
  }

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public PersistedRole setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public PersistedRole copy() {
    return new PersistedRole();
  }
}
