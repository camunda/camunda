/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.user;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;

public class PersistedUser extends UnpackedObject implements DbValue {

  private final ObjectProperty<UserRecord> userProp =
      new ObjectProperty<>("user", new UserRecord());

  public PersistedUser() {
    super(1);
    declareProperty(userProp);
  }

  public PersistedUser copy() {
    final var copy = new PersistedUser();
    copy.copyFrom(this);
    return copy;
  }

  public UserRecord getUser() {
    return userProp.getValue();
  }

  public void setUser(final UserRecord record) {
    userProp.getValue().copyFrom(record);
  }

  public long getUserKey() {
    return getUser().getUserKey();
  }

  public String getUsername() {
    return getUser().getUsername();
  }

  public String getName() {
    return getUser().getName();
  }

  public String getEmail() {
    return getUser().getEmail();
  }

  public String getPassword() {
    return getUser().getPassword();
  }
}
