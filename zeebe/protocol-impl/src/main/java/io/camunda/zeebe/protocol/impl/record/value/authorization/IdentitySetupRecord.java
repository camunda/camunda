/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.IdentitySetupRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;

public class IdentitySetupRecord extends UnifiedRecordValue implements IdentitySetupRecordValue {

  private final ObjectProperty<RoleRecord> defaultRoleProp =
      new ObjectProperty<>("defaultRole", new RoleRecord());
  private final ObjectProperty<UserRecord> defaultUserProp =
      new ObjectProperty<>("defaultUser", new UserRecord());

  public IdentitySetupRecord() {
    super(2);
    declareProperty(defaultRoleProp).declareProperty(defaultUserProp);
  }

  @Override
  public RoleRecordValue getDefaultRole() {
    return defaultRoleProp.getValue();
  }

  public IdentitySetupRecord setDefaultRole(final RoleRecord role) {
    defaultRoleProp.getValue().copy(role);
    return this;
  }

  @Override
  public UserRecordValue getDefaultUser() {
    return defaultUserProp.getValue();
  }

  public IdentitySetupRecord setDefaultUser(final UserRecord user) {
    defaultUserProp.getValue().copy(user);
    return this;
  }
}
