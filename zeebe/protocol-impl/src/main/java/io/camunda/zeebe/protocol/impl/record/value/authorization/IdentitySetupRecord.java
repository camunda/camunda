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
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.IdentitySetupRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;

public class IdentitySetupRecord extends UnifiedRecordValue implements IdentitySetupRecordValue {

  private final ObjectProperty<RoleRecord> defaultRoleProp =
      new ObjectProperty<>("defaultRole", new RoleRecord());
  private final ObjectProperty<UserRecord> defaultUserProp =
      new ObjectProperty<>("defaultUser", new UserRecord());
  private final ObjectProperty<TenantRecord> defaultTenantProp =
      new ObjectProperty<>("defaultTenant", new TenantRecord());

  public IdentitySetupRecord() {
    super(3);
    declareProperty(defaultRoleProp)
        .declareProperty(defaultUserProp)
        .declareProperty(defaultTenantProp);
  }

  @Override
  public RoleRecordValue getDefaultRole() {
    return defaultRoleProp.getValue();
  }

  public IdentitySetupRecord setDefaultRole(final RoleRecord role) {
    defaultRoleProp.getValue().copyFrom(role);
    return this;
  }

  @Override
  public UserRecordValue getDefaultUser() {
    return defaultUserProp.getValue();
  }

  public IdentitySetupRecord setDefaultUser(final UserRecord user) {
    defaultUserProp.getValue().copyFrom(user);
    return this;
  }

  @Override
  public TenantRecordValue getDefaultTenant() {
    return defaultTenantProp.getValue();
  }

  public IdentitySetupRecord setDefaultTenant(final TenantRecord tenant) {
    defaultTenantProp.getValue().copyFrom(tenant);
    return this;
  }
}
