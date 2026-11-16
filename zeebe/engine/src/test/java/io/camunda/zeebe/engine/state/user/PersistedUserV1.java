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
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecordV1;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class PersistedUserV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<UserRecordV1> userProp =
      new ObjectProperty<>("user", new UserRecordV1());

  public PersistedUserV1() {
    super(1);
    declareProperty(userProp);
  }

  public PersistedUserV1 copy() {
    final var copy = new PersistedUserV1();
    copy.copyFrom(this);
    return copy;
  }

  public UserRecordV1 getUser() {
    return userProp.getValue();
  }

  public void setUser(final UserRecordV1 record) {
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
